# Recom3 Ghost

HUD autónomo para grabar una bajada y repetirla contra una referencia en Recon Snow2. Guarda una referencia en el almacenamiento privado de la app. Reutiliza las fuentes y el cargador de fuentes de `reconUI`. El tema de pantalla completa y el manejo del mando siguen el ejemplo de Snow2Liftie por eventos DPAD.

## Vista del HUD

<img src="docs/recom3-ghost-hud.png" width="428" alt="HUD de Recom3 Ghost con tres segundos de ventaja, velocidad, distancia y trazado de la bajada">

*Recreación de la pantalla durante una bajada, con datos de ejemplo.*

## Uso

1. Abre **Recom3 Ghost** en el HUD y espera a **GPS LISTO**.
2. En la salida, pulsa **OK** para grabar. La grabación empieza con la siguiente posición válida. Pulsa **OK** al terminar la bajada para guardar la referencia.
3. Vuelve a la misma salida y pulsa **OK** para comparar. El tiempo negativo indica que vas por delante. Pulsa **OK** cerca de la meta para cerrar la comparación.
4. Desde la pantalla inicial, pulsa **derecha** y luego **OK** para grabar otra referencia. **Atrás** o **izquierda** cancelan la grabación. La referencia anterior se conserva hasta que se guarda una nueva.

El HUD actualiza la diferencia cuando puede asociar la posición a un tramo cercano del recorrido guardado. La diferencia se muestra en segundos enteros. Las grabaciones se hacen con el HUD en primer plano y se cancelan al salir de la pantalla. Una grabación con un hueco GPS largo no se puede guardar como referencia.

## Compilación e instalación

Usa JDK 8, Android SDK Platform 27 y Build Tools 27.0.3. Configura `ANDROID_HOME` o crea un `local.properties` con `sdk.dir`. La APK usa API mínima y objetivo 16 para Snow2. El proyecto usa Gradle 4.1 y Android Gradle Plugin 3.0.1.

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

En Windows, si el Gradle antiguo no puede resolver sus dependencias, ejecuta `.\build-apk.ps1` con `JAVA_HOME` y `ANDROID_HOME` configurados. Genera y verifica la APK en la misma ruta. Conserva el mismo keystore de depuración para poder actualizarla con `adb install -r`.

Para validar la comparación en el dispositivo, graba dos pasadas por el mismo recorrido y comprueba el GPS, el mando y la estabilidad de la diferencia, en especial en los giros y cuando se pierde y recupera señal.

## Licencias

- El código propio de Recom3 Ghost se distribuye bajo la [licencia MIT](LICENSE).
- El código reutilizado de Recon Instruments se distribuye bajo la [Artistic License 2.0](reconUI/LICENSE). El [README del módulo](reconUI/README.md) describe la adaptación.
- Las fuentes Open Sans incluidas se distribuyen bajo la [Apache License 2.0](reconUI/LICENSE-OpenSans.txt).
