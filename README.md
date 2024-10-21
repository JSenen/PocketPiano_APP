# Acupet App BLE OTA
---

![Android](https://img.shields.io/badge/android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
---

## Objetivo de la Aplicación:
1.- Proceder, por BLE (Bluetooth Low Energy) a la actualización del firmware del dispositivo via "Over The Air" (OTA)


---
## OTA (BLE)

Se utiliza el protocolo con el siguiente **diagrama de secuencia**:

![Imagen](https://github.com/JSenen/esp-idf-provisioning-android/blob/master/doc/Sequence_BLE_OTA.png)

## OTA (BLE) - SCREENSHOTS SEQUENCE

![Imagen](https://github.com/JSenen/HUB_PocketPiano_OTA/blob/main/doc/Piano_OTA_Screen.png)

## CONTROL 

Para las distitnas configuraciones en los tiempos de teclas del piano, se utiliza la siguiente secuencia

![Imagen](https://github.com/JSenen/HUB_PocketPiano_OTA/blob/fix/doc/Piano_Control_Screen.png)

## Comunicación con Dispositivo BLE

### 1. **IntentFilter y el receptor de radio (BroadcastReceiver)**

El método `makeGattUpdateIntentFilter()` se encarga de capturar las siguientes acciones relacionadas con la conexión GATT:

- **`ACTION_GATT_CONNECTED`**: El dispositivo BLE se conecta.
- **`ACTION_GATT_DISCONNECTED`**: El dispositivo BLE se desconecta.
- **`ACTION_GATT_SERVICES_DISCOVERED`**: Se descubren los servicios GATT en el dispositivo BLE.
- **`ACTION_DATA_AVAILABLE`**: Los datos están disponibles para ser procesados.

Este `IntentFilter` es fundamental para recibir actualizaciones del estado de la conexión BLE y gestionar las acciones correspondientes en la aplicación.

### 2. **Interacción con el Dispositivo BLE mediante Radio Buttons**

El método `onRadioButtonClicked(View view)` se activa cuando el usuario selecciona un `RadioButton` en la interfaz. Dependiendo de la opción seleccionada (0-6), se envían comandos BLE al dispositivo. Estos son los pasos principales:

1. **Vaciar los campos de las teclas** (`mKey1Val`, `mKey2Val`, etc.).
2. **Seleccionar el módulo**: Se asigna un valor a `RadioOption` según el botón seleccionado (0 a 6).
3. **Enviar comandos BLE**:
    - **Solicitar número de serie**: Comando `0x10` con el módulo seleccionado.
    - **Solicitar versión del software**: Comando `0x0E` con el módulo seleccionado.
    - **Habilitar teclas**: Comando `0x23` con la máscara correspondiente al módulo.

Estos comandos se envían al dispositivo a través de la característica `mRxCharacteristic` y se usan `Handler` para retrasar los comandos y evitar saturar el proceso de escritura.

### 3. **Leer y Escribir Niveles de Sonido (Read/Write Level)**

#### **`sendReadLevel(View view)`**
Este método envía una serie de comandos para leer los niveles de sonido o parámetros del dispositivo BLE:

- **Comando**: `0x26` para leer los niveles.
- **Teclas blancas y negras**: `value[1]` indica si la tecla es blanca (0) o negra (1), y `value[2]` especifica el nivel (0-7).

El proceso se repite para los distintos niveles, usando un `Handler` para retrasar los envíos.

#### **`sendWriteLevel(View view)`**
Este método escribe nuevos valores en los niveles de sonido del dispositivo. Toma los valores ingresados en los campos `EditText` y envía comandos para cada nivel:

- **Comando**: `0x25` para escribir los niveles.
- **Teclas blancas y negras**: Al igual que en la lectura, `value[1]` indica si la tecla es blanca (0) o negra (1), y `value[2]` corresponde al nivel.

Los valores son convertidos a enteros y enviados al dispositivo BLE usando la característica `mRxCharacteristic`, con la misma estrategia de retrasos controlados con `Handler`.

### Resumen

El código se utiliza para gestionar la interacción con un dispositivo BLE, donde los radio buttons permiten seleccionar módulos, y se envían comandos para obtener datos como el número de serie y la versión del software. Además, se gestionan los niveles de las teclas (blancas y negras), permitiendo tanto la lectura como la escritura de los valores.

El uso de `Handler` para programar retrasos asegura que las operaciones se envíen en secuencia sin saturar la conexión BLE.


# BLE Commands

| Command                | Parameter 1       | Parameter 2   | Parameter 3    | Parameter 4     | Parameter 5 |
|------------------------|-------------------|---------------|----------------|-----------------|-------------|
| **Unlock**             | **Val**            |               |                |                 |             |
| `00`                   | `EF20`             |               |                |                 |             |
| **Lock**               | **Val**            |               |                |                 |             |
| `01`                   | `EF20`             |               |                |                 |             |
| **Set Main Sound**      | **Pos** `0-7`      | **On** `0/1`  | **Bank**       | **Patch**       | **Vol**     |
| `02`                   | `0-7`              | `0-Off/1-On`  | `0-128`        | `0-100`         |             |
| **Get Main Sound**      | **Pos** `0-7`      |               |                |                 |             |
| `03`                   | `0-7`              |               |                |                 |             |
| **Notify Main Sound**   | **Pos** `0-7`      | **On**        | **Bank**       | **Patch**       | **Vol**     |
| `04`                   | `0-7`              | `0-Off/1-On`  | `0-128`        | `0-100`         |             |
| **Set Sub Sound**       | **Value**          |               |                |                 |             |
| `05`                   | `0-128`            |               |                |                 |             |
| **Get Sub Sound**       | **Pos** `0-7`      |               |                |                 |             |
| `06`                   | `0-7`              |               |                |                 |             |
| **Notify Sub Sound**    | **Value**          |               |                |                 |             |
| `07`                   | `0-128`            |               |                |                 |             |
| **Set Preset**          | **PosMain** `0-7`  | **PosSub**    | **Value**      |                 |             |
| `08`                   | `0-7`              | `0-128`       |                |                 |             |
| **Get Preset**          | **PosMain** `0-7`  |               |                |                 |             |
| `09`                   | `0-7`              |               |                |                 |             |
| **Notify Preset**       | **Value**          |               |                |                 |             |
| `0A`                   | `0-7`              | `0-128`       |                |                 |             |
| **Set Volume**          | **Value**          |               |                |                 |             |
| `0B`                   | `127-0`            |               |                |                 |             |
| **Get Volume**          | **Value**          |               |                |                 |             |
| `0C`                   |                    |               |                |                 |             |
| **Notify Volume**       | **Value**          |               |                |                 |             |
| `0D`                   |                    |               |                |                 |             |
| **Get Software Version**| **Module** `0-10`  |               | **Major**      | **Minor**       |             |
| `0E`                   |                    |               |                |                 |             |
| **Notify Software Version**| **Module** `0-10` | **Major**  | **Minor**     |                 |             |
| `0F`                   |                    |               |                |                 |             |
| **Get Serial Number**   | **Module** `0-8`   |               |                |                 |             |
| `10`                   |                    |               |                |                 |             |
| **Notify Serial Number**| **Module** `0-8`   |               | **Value**      |                 |             |
| `11`                   |                    |               |                |                 |             |

# Key Offset Commands

| Command                | Parameter 1   | Parameter 2   | Parameter 3 | Parameter 4 | Parameter 5 | Notes               |
|------------------------|---------------|---------------|-------------|-------------|-------------|---------------------|
| **Set Key Offset**      | **Module** `0-7` | **Key** `1-12` | **Value**   |             |             |                     |
| `20`                   | `0-7`         | `1-12`         | `32766`     |             |             | Octave 0 is Z, key 1-3 |
| **Get Key Offset**      | **Module** `0-7` | **Key** `1-12` |             |             |             |                     |
| `21`                   | `0-7`         | `1-12`         |             |             |             |                     |
| **Notify Key Offset**   | **Module** `0-7` | **Key** `1-12` | **Value**   |             |             |                     |
| `22`                   | `0-7`         | `1-12`         | `32766`     |             |             |                     |

# Key Time Commands

| Command                | Parameter 1   | Parameter 2   | Parameter 3 | Parameter 4 | Parameter 5 | Notes               |
|------------------------|---------------|---------------|-------------|-------------|-------------|---------------------|
| **Set Key Time**        | **Type** `White/Black` | **Pos** `0-7` | **Value**   |             |             |                     |
| `23`                   | `White/Black` | `PPP Low` `0-65535` |             |             |             |                     |
| **Get Key Time**        | **Type** `White/Black` | **Pos** `0-7` |             |             |             |                     |
| `24`                   | `White/Black` | `PPP Low` `0-65535` |             |             |             |                     |
| **Notify Key Time**     | **Type** `White/Black` | **Pos** `0-7` | **Value**   |             |             |                     |
| `25`                   | `White/Black` | `PPP Low` `0-65535` |             |             |             |                     |



## License  
  

    Copyright 2020 Espressif Systems (Shanghai) PTE LTD  
     
    Licensed under the Apache License, Version 2.0 (the "License");  
    you may not use this file except in compliance with the License.  
    You may obtain a copy of the License at  
     
        http://www.apache.org/licenses/LICENSE-2.0  
     
    Unless required by applicable law or agreed to in writing, software  
    distributed under the License is distributed on an "AS IS" BASIS,  
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
    See the License for the specific language governing permissions and  
    limitations under the License.
