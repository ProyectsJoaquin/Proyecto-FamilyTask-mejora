package com.hogarsmart.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
    private static final UUID COMMAND_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ac");
    private static final UUID STATUS_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ad");
    private static final int REQ_PERMS = 42;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView connectionLabel, taskLabel, statusLabel;
    private EditText nameInput, endpointInput, tokenInput, adminPinInput;
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private boolean scanning = false;
    private boolean isAdmin = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("hogarsmart", MODE_PRIVATE);
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        buildHome();
        requestNeededPermissions();
    }

    private void buildHome() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 24, 24, 24); root.setBackgroundColor(Color.rgb(245,248,247));
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = text("HogarSmart", 30, Color.rgb(16,55,48)); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = text("Organización inteligente del hogar", 14, Color.DKGRAY); subtitle.setGravity(Gravity.CENTER); root.addView(subtitle);
        connectionLabel = text("Bluetooth: desconectado", 15, Color.DKGRAY); root.addView(connectionLabel);
        nameInput = field("Nombre del integrante", prefs.getString("name", "")); root.addView(nameInput);
        Button scan = button("Buscar y conectar ESP32-C3"); root.addView(scan); scan.setOnClickListener(v -> startScan());
        Button free = button("ESTOY LIBRE · Consultar próxima tarea"); root.addView(free); free.setOnClickListener(v -> sendAvailability(true));
        Button busy = button("ESTOY OCUPADO"); root.addView(busy); busy.setOnClickListener(v -> sendAvailability(false));
        taskLabel = text("Tu próxima tarea aparecerá aquí.", 18, Color.rgb(25,65,58)); taskLabel.setPadding(0,24,0,12); root.addView(taskLabel);
        statusLabel = text("Selecciona tu nombre y conecta el dispositivo.", 14, Color.DKGRAY); root.addView(statusLabel);
        Button refresh = button("Consultar Google Calendar"); root.addView(refresh); refresh.setOnClickListener(v -> fetchTask());
        Button admin = button("Acceso administrador"); root.addView(admin); admin.setOnClickListener(v -> showAdmin());
        Button settings = button("Configuración de conexión"); root.addView(settings); settings.setOnClickListener(v -> showSettings());
        TextView foot = text("HogarSmart · BLE + Google Calendar", 12, Color.GRAY); foot.setGravity(Gravity.CENTER); root.addView(foot);
    }

    private TextView text(String s, int size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,10,0,10); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); b.setLayoutParams(p); return b; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value); return e; }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_PERMS);
            }
        } else if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMS);
        }
    }

    private boolean hasBlePermission() {
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startScan() {
        if (adapter == null || !adapter.isEnabled()) { toast("Activa Bluetooth en Android."); return; }
        if (!hasBlePermission()) { requestNeededPermissions(); return; }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner(); if (scanner == null) { toast("No se pudo iniciar BLE."); return; }
        connectionLabel.setText("Bluetooth: buscando HogarSmart-ESP32..."); scanning = true;
        try { scanner.startScan(scanCallback); } catch (SecurityException ex) { toast("Permiso Bluetooth denegado."); return; }
        handler.postDelayed(() -> { if (scanning) { try { scanner.stopScan(scanCallback); } catch (Exception ignored) {} scanning = false; connectionLabel.setText("No se encontró el ESP32. Verifica que esté encendido."); } }, 12000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            BluetoothDevice device = result.getDevice(); String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
            if (name == null) { try { name = device.getName(); } catch (SecurityException ignored) {} }
            if (name != null && (name.contains("HogarSmart") || name.contains("ESP32-C3"))) {
                scanning = false; try { adapter.getBluetoothLeScanner().stopScan(this); gatt = device.connectGatt(MainActivity.this, false, gattCallback); connectionLabel.setText("Bluetooth: conectando a " + name); } catch (SecurityException ex) { toast("Permiso Bluetooth denegado."); }
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            runOnUiThread(() -> connectionLabel.setText(newState == BluetoothProfile.STATE_CONNECTED ? "Bluetooth: conectado" : "Bluetooth: desconectado"));
            if (newState == BluetoothProfile.STATE_CONNECTED) { if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return; g.discoverServices(); }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) { commandCharacteristic = null; }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service != null) { commandCharacteristic = service.getCharacteristic(COMMAND_UUID); runOnUiThread(() -> statusLabel.setText("ESP32 listo. Escribe tu nombre y selecciona LIBRE u OCUPADO.")); }
            else runOnUiThread(() -> statusLabel.setText("El ESP32 no ofrece el servicio BLE esperado. Revisa el firmware.") );
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { String value = new String(c.getValue(), StandardCharsets.UTF_8); runOnUiThread(() -> handleDeviceMessage(value)); }
    };

    private void writeBle(String value) {
        if (gatt == null || commandCharacteristic == null) { toast("Primero conecta el ESP32-C3."); return; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { requestNeededPermissions(); return; }
        try {
            commandCharacteristic.setValue((value + "\n").getBytes(StandardCharsets.UTF_8));
            commandCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            boolean ok = gatt.writeCharacteristic(commandCharacteristic);
            if (!ok) toast("No se pudo enviar el mensaje BLE.");
        } catch (Exception ex) { toast("Error BLE: " + ex.getMessage()); }
    }

    private void sendAvailability(boolean free) {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) { toast("Escribe tu nombre primero."); return; }
        prefs.edit().putString("name", name).apply();
        writeBle("USER:" + name.toUpperCase(Locale.ROOT));
        writeBle(free ? "FREE" : "BUSY");
        if (free) fetchTask(); else statusLabel.setText("Disponibilidad registrada: OCUPADO");
    }

    private void fetchTask() {
        String name = nameInput.getText().toString().trim();
        String endpoint = prefs.getString("endpoint", ""); String token = prefs.getString("token", "");
        if (name.isEmpty()) { toast("Escribe tu nombre."); return; }
        if (endpoint.isEmpty() || token.isEmpty()) { statusLabel.setText("Configura primero la URL de Apps Script y el token."); return; }
        statusLabel.setText("Consultando Google Calendar...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = endpoint + "?token=" + enc(token) + "&user=" + enc(name);
                conn = (HttpURLConnection)new URL(url).openConnection(); conn.setConnectTimeout(10000); conn.setReadTimeout(10000); conn.setRequestMethod("GET");
                String body = read(conn); JSONObject obj = new JSONObject(body);
                runOnUiThread(() -> {
                    if (!obj.optBoolean("ok")) { statusLabel.setText("Error: " + obj.optString("error", "No se pudo consultar")); return; }
                    if (!obj.optBoolean("found")) { taskLabel.setText("No tienes tareas pendientes."); statusLabel.setText("Google Calendar consultado correctamente."); return; }
                    String task = obj.optString("nombre", "Tarea"); String start = obj.optString("inicio", ""); String id = obj.optString("id", "");
                    taskLabel.setText("PRÓXIMA TAREA\n" + task + "\nResponsable: " + name + "\nInicio: " + start);
                    statusLabel.setText("Tarea recibida. El ESP32 también debe consultar el calendario para activar la alarma a la hora indicada.");
                    writeBle("TASK_ID:" + id); writeBle("TASK:" + task);
                });
            } catch (Exception ex) { runOnUiThread(() -> statusLabel.setText("No se pudo consultar Calendar: " + ex.getMessage())); }
            finally { if (conn != null) conn.disconnect(); }
        }).start();
    }

    private String read(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode(); java.io.InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line); r.close(); return b.toString();
    }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private void handleDeviceMessage(String raw) {
        try { JSONObject o = new JSONObject(raw); if (o.has("task")) taskLabel.setText("PRÓXIMA TAREA\n" + o.optString("task")); if (o.has("state")) statusLabel.setText("ESP32: " + o.optString("state")); }
        catch (Exception ignored) { statusLabel.setText("ESP32: " + raw); }
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(24,12,24,12);
        EditText endpoint = field("URL de implementación de Apps Script", prefs.getString("endpoint", ""));
        EditText token = field("Token compartido", prefs.getString("token", "")); panel.addView(endpoint); panel.addView(token);
        new android.app.AlertDialog.Builder(this).setTitle("Configuración de Google Calendar").setView(panel)
          .setPositiveButton("Guardar", (d,w) -> { prefs.edit().putString("endpoint", endpoint.getText().toString().trim()).putString("token", token.getText().toString().trim()).apply(); toast("Configuración guardada."); })
          .setNegativeButton("Cancelar", null).show();
    }

    private void showAdmin() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(24,12,24,12);
        EditText pin = field("PIN de administrador", ""); panel.addView(pin);
        new android.app.AlertDialog.Builder(this).setTitle("Administrador").setMessage("El administrador gestiona los eventos desde el calendario compartido.").setView(panel)
          .setPositiveButton("Entrar", (d,w) -> {
              String expected = prefs.getString("admin_pin", "1234");
              if (pin.getText().toString().equals(expected)) {
                  new android.app.AlertDialog.Builder(this).setTitle("Panel administrador")
                    .setItems(new String[]{"Abrir Google Calendar", "Configurar URL y token", "Cambiar PIN"}, (dialog, which) -> {
                        if (which == 0) { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://calendar.google.com/calendar/u/0/r")); startActivity(i); }
                        else if (which == 1) showSettings(); else changePin();
                    }).show();
              } else toast("PIN incorrecto.");
          }).setNegativeButton("Cancelar", null).show();
    }

    private void changePin() {
        EditText p = field("Nuevo PIN de administrador", ""); new android.app.AlertDialog.Builder(this).setTitle("Cambiar PIN").setView(p).setPositiveButton("Guardar", (d,w) -> { String v=p.getText().toString().trim(); if(v.length()>=4) { prefs.edit().putString("admin_pin",v).apply(); toast("PIN actualizado."); } else toast("Usa al menos 4 caracteres."); }).setNegativeButton("Cancelar",null).show();
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { super.onDestroy(); if (gatt != null) { try { gatt.close(); } catch (Exception ignored) {} } }
}
