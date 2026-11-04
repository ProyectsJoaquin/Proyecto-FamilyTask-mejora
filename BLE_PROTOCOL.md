# Protocolo BLE HogarSmart para ESP32-C3 Mini

La app Android busca un periférico BLE cuyo nombre contenga `HogarSmart` o `ESP32-C3`.

UUIDs (deben coincidir exactamente en el firmware):
- Servicio: `12345678-1234-1234-1234-1234567890ab`
- Característica de escritura (app -> ESP32): `12345678-1234-1234-1234-1234567890ac`
- Característica de estado/notificación (ESP32 -> app): `12345678-1234-1234-1234-1234567890ad`

Mensajes de texto UTF-8 terminados en salto de línea:
- `USER:NOMBRE`
- `FREE`
- `BUSY`
- `TASK_ID:ID_DE_EVENTO`
- `TASK:Título de la tarea`

Mensajes de notificación recomendados desde ESP32:
- `{"state":"Conectado"}`
- `{"task":"Lavar los platos"}`
- `{"state":"Alarma activa"}`
- `{"state":"Cuenta regresiva"}`
- `{"state":"Tarea cumplida"}`

El ESP32 debe conectarse a Wi-Fi y consultar el endpoint de Apps Script para obtener el evento y su hora de inicio. Al completarse la tarea, debe hacer POST JSON:
`{"token":"TOKEN","action":"complete","id":"EVENT_ID"}`

La app Android implementada envía los mensajes y consulta el backend cuando el usuario pulsa LIBRE. La lógica de alarma, cuenta regresiva y confirmación de finalización debe ejecutarse en el firmware ESP32; la app no puede activar físicamente el buzzer/LED por sí sola.
