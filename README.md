# HogarSmart Android

Proyecto Android nativo (Java) para Android 8.0+ con interfaz en español, búsqueda BLE, identificación del integrante, estados LIBRE/OCUPADO, consulta de la próxima tarea mediante Google Apps Script, configuración del endpoint/token y acceso de administrador a Google Calendar.

## Importante: estado de entrega

Este paquete contiene el proyecto fuente listo para compilar, pero no incluye un APK binario. El entorno donde se preparó no dispone del Android SDK ni de Gradle, por lo que no es posible compilar ni firmar un APK verificable aquí. El workflow de GitHub Actions incluido compila un APK debug al subir el proyecto a un repositorio GitHub.

## Generar APK

1. Crea un repositorio GitHub y sube todo el contenido de esta carpeta.
2. En GitHub, abre la pestaña Actions y ejecuta `Build HogarSmart APK` (o espera a que corra al hacer push).
3. Descarga el artefacto `HogarSmart-debug-apk`.
4. Extrae `app-debug.apk` e instálalo en Android. Para instalarlo, permite instalar aplicaciones desde esa fuente cuando Android lo solicite.

También puedes abrir la carpeta en Android Studio y ejecutar `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

## Configuración Google Calendar

1. Con la cuenta administradora crea un calendario llamado `TAREAS DOMÉSTICAS`.
2. En Configuración e integración del calendario, copia el ID del calendario.
3. Abre https://script.google.com/ y crea un proyecto.
4. Copia `backend/GoogleAppsScript.gs` en el editor.
5. Reemplaza `CALENDAR_ID` y `ACCESS_TOKEN` por el ID real y un token aleatorio largo.
6. Ejecuta/autoriza el script y despliega como Aplicación web, ejecutando como tu cuenta. Copia la URL terminada en `/exec`.
7. En HogarSmart > Configuración, pega URL y token.
8. Crea eventos con descripción que incluya exactamente estas líneas, cambiando el nombre:
   `RESPONSABLE: Carlos`
   `ESTADO: PENDIENTE`

El nombre del responsable se compara sin distinguir mayúsculas/minúsculas. El título del evento es el nombre de la tarea.

## Bluetooth / ESP32

La app busca un dispositivo cuyo nombre contenga `HogarSmart` o `ESP32-C3`. UUIDs y protocolo en `firmware/BLE_PROTOCOL.md`. El firmware del ESP32 debe implementar ese servicio BLE, consultar Apps Script por Wi-Fi, comparar la hora, manejar LED/buzzer/touch y enviar el POST de finalización. El APK por sí solo no sustituye el firmware.

## Funciones incluidas

- Buscar y conectar un periférico BLE con el servicio HogarSmart.
- Enviar nombre, `FREE` y `BUSY`.
- Consultar la siguiente tarea del integrante en Google Calendar a través de Apps Script.
- Mostrar título, responsable y hora devuelta por el backend.
- Configurar URL y token localmente.
- Acceso de administrador con PIN local inicial `1234`, apertura de Google Calendar y cambio de PIN.

## Seguridad y limitaciones

- Cambia el PIN inicial y utiliza un token largo. El token se guarda localmente en el teléfono.
- El PIN de administrador es una barrera de interfaz local, no un sistema de autenticación empresarial.
- Para una instalación familiar real, limita quién puede editar el calendario y protege el endpoint. No publiques el token en repositorios públicos.
- La app consulta la próxima tarea al pulsar LIBRE o Consultar. Para alertas fiables, el ESP32 también debe consultar el calendario por Wi-Fi y conservar la tarea/horario localmente.
- El servicio de Apps Script de ejemplo devuelve el evento pendiente más próximo dentro de los próximos 30 días.
