/*
 * ===================== Zooz Scene Controller (ZEN32) Driver =====================
 *
 *  Copyright 2024 Robert Morris
 *  
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 * 
 * BUTTON NUMBER/EVENT MAPPING:
 * 
 * "Base" button number:
 *   - relay/large button = button 5
 *   - small top left = button 1
 *   - small top right = button 2
 *   - small bottom left = button 3
 *   - small bottom right = button 4
 * Single taps, hold, and release:
 *  - base button number pushed, held, and released events
 * Multi-taps:
 *  - mathematically, a pushed event for button number = (base button number)  + (5 * (number of taps - 1))
      ... or specifically:
 *     * "button 1" taps: button 1, 6, 11, 16, or 21 pushed (taps 1-5)
 *     * "button 2" taps: button 2, 7, 12, 17, or 22 pushed (taps 1-5)
 *     * "button 3" taps: button 3, 8, 13, 18, or 23 pushed (taps 1-5)
 *     * "button 4" taps: button 4, 9, 14, 19, or 24 pushed (taps 1-5)
 *     * "button 5" taps: button 5, 10, 15, 20, or 25 pushed (taps 1-5)
 *
 * 
 *  Changelog:
 *  v2.2.2  (2024-05-29): Update fingerprint
 *  v2.2.1  (2023-12-04): Fix for parameter 18 swapped values (power restore state)
 *  v2.2.f1 (2023-10-03): @jtp10181 Saving LED human readable details to a state variables, support for doubleTapped event, other misc tweaks
 *  v2.2    (2023-09-23): Enhancements for some commands and preferences with changes thanks to @jtp10181
 *  v2.1    (2023-09-19): Update for firmware 10.40 (700-series) and hardware 2.0. Recommended for use with
 *                        hardware v2 (800LR) or original hardware with 10.40+ firmware only.
 *  v2.0.1  (2023-05-07): Superivsion response fix
 *  v2.0    (2022-02-20): Add Indicator command class support (thanks to @jtp10181); requires ZEN32 firmware 10.10 or greater
 *  v1.0.1  (2021-04-23): Fix typo in BasicGet; pad firmware subversion with 0 as needed
 *  v1.0    (2021-04-01): Initial Release   
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x20: 1,    // Basic
   0x25: 1,    // SwitchBinary
   0x55: 1,    // TransportService
   0x59: 1,    // AssociationGroupInfo
   0x5B: 1,    // CentralScene
   0x6C: 1,    // Supervision
   0x70: 1,    // Configuration
   0x72: 2,    // ManufacturerSpecific
   0x85: 1,    // Association
   0x86: 2,    // Version
   0x87: 3,    // Indicator
   0x8E: 2,    // MultichannelAssociation
   0x98: 1,    // Security
   0x9F: 1     // Security S2
]

// color name to parameter value mapping:
@Field static final TreeMap<Integer,String> colorNameMap = [
   0:"white",
   1:"blue",
   2:"green",
   3:"red",
   4:"magenta",
   5:"yellow",
   6:"cyan"
]

// LED/button number to parameter value mappings (for LED color parameters):
@Field static final Map<Integer,Integer> ledIndicatorParams = [1: 2, 2: 3, 3: 4, 4: 5, 5: 1]
@Field static final Map<Integer,Integer> ledColorParams = [1: 7, 2: 8, 3: 9, 4: 10, 5: 6]
@Field static final Map<Integer,Integer> ledBrightnessParams = [1: 12, 2: 13, 3: 14, 4: 15, 5: 11]

// LED number mappings for Indicator command class:
@Field static Map<Integer,Short> indicatorLEDNumberMap = [0:0x50, 5:0x43, 1:0x44, 2:0x45, 3:0x46, 4:0x47]

@Field static final Map zwaveParameters = [
   16: [input: [name: "param.16", type: "number", title: "Auto-Off Timer for Relay (minutes) [#16]", range: 0..65535],
      size: 4],
   17: [input: [name: "param.17", type: "number", title: "Auto-On Timer for Relay (minutes) [#17]", range: 0..65535],
      size: 4],
   18: [input: [name: "param.18", type: "enum", title: "State After Power Restored [#18]",
      options: [0:"Previous State (DEFAULT)", 1:"Off", 2:"On"]],
      size: 1],
   19: [input: [name: "param.19", type: "enum", title: "Physical and Z-Wave (Smart Bulb Mode) [#19]",
      options: [0:"Disable physical control, enable Z-Wave", 1:"Enable physical and Z-Wave control (DEFAULT)", 2:"Disable physical and Z-Wave control"]],
      size: 1],
   20: [input: [name: "param.20", type: "enum", title: "Behavior if Control is Disabled [#20]", description:" - Button events are always sent",
      options: [0:"Send on/off reports and change LED",1:"Do not send on/off reports or change LED (DEFAULT)"]],
      size: 1],
   21: [input: [name: "param.21", type: "enum", title: "3-Way Switch Type [#21]",
      options: [0:"Regular Mechanical 3-way (DEFAULT)",1:"Momentary Switch"]],
      size: 1],
   22: [input: [name: "param.22", type: "enum", title: "Programming from the Relay Button [#22]",
      options: [0:"Enabled (DEFAULT)", 1:"Disabled"]],
      size: 1],
   23: [input: [name: "param.23", type: "enum", title: "LED Flash when Settings Changed [#23]",
      options: [0:"Flash Enabled (DEFAULT)",1:"Flash Disabled"]],
      size: 1],
   24: [input: [name: "param.24", type: "enum", title: "Scene Control Events on Relay [#24]",
      options: [0:"Enabled (DEFAULT)", 1:"Disabled"]],
      size: 1],
   26: [input: [name: "param.26", type: "enum", title: "Scene Control Events from 3-way [#26]",
      options: [0:"Disabled (DEFAULT)", 1:"Enabled"]],
      size: 1],
   25: [input: [name: "param.25", type: "enum", title: "Send Status Report to Associations [#25]",
      options: [ 0:"None", 1:"Physical Tap On ZEN Only", 2:"Physical Tap On Connected 3-Way Switch Only", 3:"Physical Tap On ZEN / 3-Way Switch",
			4:"Z-Wave Command From Hub", 5:"Physical Tap On ZEN / Z-Wave Command", 6:"Physical Tap On 3-Way Switch / Z-Wave Command",
			7:"Physical Tap On ZEN / 3-Way Switch / Z-Wave Command", 8:"Timer Only", 9:"Physical Tap On ZEN / Timer",
			10:"Physical Tap On 3-Way Switch / Timer", 11:"Physical Tap On ZEN / 3-Way Switch / Timer", 12:"Z-Wave Command From Hub / Timer",
			13:"Physical Tap On ZEN / Z-Wave Command / Timer", 14:"Physical Tap On ZEN / 3-Way Switch / Z-Wave Command / Timer",
			15:"All Of The Above (DEFAULT)" ]],
      size: 1]
]

// These get set by configure(), "default" parameter values necessary to make this driver work as expected and generally not exposed in the UI:
@Field static final Map defaultZwaveParameters = [
   // LED indicator mode for relay and buttons 1-4:
   //1: [value: 3, size: 1], // leaving this one (relay LED) out since it's set by separate preference
   2: [value: 3, size: 1], // these could default to 2 or 3 (always off or always on); defaulting to 3 for now, but could read/compare in future and not override if set to one...
   3: [value: 3, size: 1],
   4: [value: 3, size: 1],
   5: [value: 3, size: 1]
]

metadata {
   definition (name: "Zooz Scene Controller (ZEN32)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zen32-scene-ctlr.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "Configuration"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "DoubleTapableButton"

      command "refresh"

      command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]

      command "setLED", [[name:"ledNumber*", type: "NUMBER", description: "LED/Button Number (1-5, 5=large button)", constraints: 1..5],
                         [name:"colorName", type: "ENUM", description: "Select Color Name (blank for no change)", constraints: ['':"no change"]+colorNameMap],
                         [name:"brightness", type: "NUMBER", description: "Brightness Level (100, 60, 30; 0 for off; blank turns on only)", constraints: [100,60,30,0]],
                        ]

      command "setIndicator", [[name:"ledNumber*", type: "NUMBER", description: "LED/Button number (1-5, 5=large button, 0=all)", constraints: 0..5],
                         [name:"mode*", type: "ENUM", description: "Mode (flash, on, or off)", constraints: ["flash", "on", "off"]],
                         [name:"lengthOfOnOffPeriods", type: "NUMBER", description: "On/off period length in tenths of seconds (0-254, e.g., 10 = 1 second)", constraints: 2..255],
                         [name:"numberOfOnOffPeriods", type: "NUMBER", description: "Number of total on/off periods (1-254), or 255 for indefinite", constraints: 1..255],
                         [name:"lengthOfOnPeriod", type: "NUMBER", description: "On period length in tenths of seconds (e.g., 8 = 0.8 seconds; can be used to create asymmetric on/off periods)", constraints: 1..254],
                        ]

      fingerprint mfr:"027A", prod:"7000", deviceId:"A008", inClusters:"0x5E,0x25,0x70,0x20,0x5B,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x87,0x9F,0x6C,0x7A", controllerType: "ZWV"
      fingerprint mfr:"027A", prod:"7000", deviceId:"A008", inClusters:"0x5E,0x9F,0x55,0x6C", controllerType: "ZWV"
   }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "relayLEDBehavior", type: "enum", title: "Relay LED Indicator Mode", options: [[0:"On when relay Off (DEFAULT)"],[1:"On when relay On"],
         [2:"Always Off"],[3:"Always On"],[4:"Control with \"Set LED\" command"]]
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void logsOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void parse(String description){
   if (enableDebug) log.debug "parse description: ${description}"
   hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
   if (cmd) {
      zwaveEvent(cmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
   sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(
         zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)
      ), hubitat.device.Protocol.ZWAVE))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
   if (enableDebug) log.debug "VersionReport: ${cmd}"
   device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${String.format("%02d", cmd.firmware0SubVersion)}")
   device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
   device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
   if (enableDebug) log.debug "DeviceSpecificReport v2: ${cmd}"
   switch (cmd.deviceIdType) {
      case 1:
         // serial number
         String serialNumber = ""
         if (cmd.deviceIdDataFormat==1) {
            cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff, 1).padLeft(2, '0')}
         } else {
            cmd.deviceIdData.each { serialNumber += (char)it }
         }
         if (enableDebug) log.debug "Device serial number is $serialNumber"
         device.updateDataValue("serialNumber", serialNumber)
         break
   }
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: ${cmd}"

   //Find LED Number for certian parameters
   def ledIndic = ledIndicatorParams.find{ cmd.parameterNumber == it.value }
   def ledColor = ledColorParams.find{ cmd.parameterNumber == it.value }
   def ledBright = ledBrightnessParams.find{ cmd.parameterNumber == it.value }
   def val = cmd.scaledConfigurationValue
   Integer ledNum
   Integer slot

   if (ledIndic) {
      slot = 0
      ledNum = ledIndic.key
      switch (val) {
         case 0: val = "on when off"; break
         case 1: val = "on when on"; break
         case 2: val = "off"; break
         case 3: val = "on"; break
      }
   }
   else if (ledColor) {
      slot = 1
      ledNum = ledColor.key
      val = colorNameMap[val as Integer] ?: "unknown"
   }
   else if (ledBright) {
      slot = 2
      ledNum = ledBright.key
      switch (val) {
         case 0: val = "100%"; break
         case 1: val = "60%"; break
         case 2: val = "30%"; break
      }
   }
   
   if (ledNum) {
      if (enableDesc) log.info "${device.displayName} LED #${ledNum} set to ${val}"
      if (state.settingsLED != null && state.settingsLED["$ledNum"] != null) {
         state.settingsLED["$ledNum"][slot] = val
      }
   }
   else {
      if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
      setStoredConfigParamValue(cmd.parameterNumber, cmd.scaledConfigurationValue)
   }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   if (enableDebug) log.debug "BasicReport:  ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}            

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.debug "BasicSet: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
   if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {    
   if (enableDebug) log.debug "CentralSceneNotification: ${cmd}"
   Integer btnBaseNum = cmd.sceneNumber ?: 0
   Integer btnNum = btnBaseNum
   String btnAction = "pushed"
   if (cmd.keyAttributes as Integer == 2) btnAction = "held"
   else if (cmd.keyAttributes as Integer == 1) btnAction = "released"
   if ((cmd.keyAttributes as Integer) >= 3) {
      btnNum = btnBaseNum + (5 * ((cmd.keyAttributes as Integer) - 2))
   }

   if (btnNum) {
      String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
      if (enableDesc) log.info "${descriptionText}"
      sendEvent(name: "${btnAction}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")

      if (cmd.keyAttributes as Integer == 3) {
         btnNum = btnBaseNum
         btnAction = "doubleTapped"
         descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
         if (enableDesc) log.info "${descriptionText}"
         sendEvent(name: "${btnAction}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd) {
   if (enableDebug) log.debug "IndicatorReport: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport cmd) {
   if (enableDebug) log.debug "IndicatorSupportedReport: ${cmd}"
   if (cmd.nextIndicatorId > 0) {
      sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.indicatorV3.indicatorSupportedGet(indicatorId:cmd.nextIndicatorId)), hubitat.device.Protocol.ZWAVE)) 
   }
}

void zwaveEvent(hubitat.zwave.Command cmd){
   if (enableDebug) log.debug "skip: ${cmd}"
}

/*
List<String> indicatorGet() {
   List<String> cmds = []
   cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSupportedGet(indicatorId:0x00))
   return delayBetween(cmds,300)
}
*/

void setStoredConfigParamValue(Integer parameterNumber, BigInteger parameterValue) {
   state."configParam${parameterNumber}" = parameterValue
}

BigInteger getStoredConfigParamValue(Integer parameterNumber) {
   return state."configParam${parameterNumber}"
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   return delayBetween([
      zwaveSecureEncap(zwave.basicV1.basicGet()),
      zwaveSecureEncap(zwave.versionV2.versionGet())
   ], 100)
}

String on() {
   if (enableDebug) log.debug "on()"
   zwaveSecureEncap(zwave.basicV1.basicSet(value: 0xFF))
}

String off() {
   if (enableDebug) log.debug "off()"
   zwaveSecureEncap(zwave.basicV1.basicSet(value: 0x00))
}

void push(btnNum) {
   sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
}

void hold(btnNum) {
   sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
}

void release(btnNum) {
   sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
}

void doubleTap(btnNum) {
   sendEvent(name: "doubleTapped", value: btnNum, isStateChange: true, type: "digital")
}

void installed(){
   log.warn "Installed..."
   sendEvent(name: "level", value: 1)
}

List<String> configure() {
   log.warn "configure..."
   List<String> cmds = []
   
   sendEvent(name: "numberOfButtons", value: 25)

   defaultZwaveParameters.each { param, data ->
      if (enableDebug) log.debug "Default parameter: setting parameter $param (size:  ${data.size}) to ${data.value}"
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: data.value as BigInteger, parameterNumber: param, size: data.size))
   }

   //Request Paramater Values
   ledIndicatorParams.each { led, pNum ->
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: pNum as Integer))
   }
   ledColorParams.each { led, pNum ->
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: pNum as Integer))
   }
   ledBrightnessParams.each { led, pNum ->
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: pNum as Integer))
   }

   cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))

   cmds = delayBetween(cmds, 150)
   cmds += updated()

   return cmds
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.info "updated..."
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, logsOff)
   }

   List<String> cmds = []
   
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Preference parameter: setting parameter $param (size: ${data.size}) to ${settings[data.input.name]}"
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   
   if (relayLEDBehavior != null) {
      BigInteger relayLEDParamVal = relayLEDBehavior as BigInteger
      if (relayLEDParamVal < 4) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: relayLEDParamVal, parameterNumber: 1, size: 1))
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 1))
      }
   }
   if (state.settingsLED == null) state.settingsLED = [1:[],2:[],3:[],4:[],5:[]]

   return delayBetween(cmds, 200)
}

//Handle when missing parameters
List<String> setLED(ledNumber) { setLED(ledNumber, null, null) }
List<String> setLED(ledNumber, String colorName) { setLED(ledNumber, colorName, null) }
List<String> setLED(ledNumber, Integer brightness) { setLED(ledNumber, null, brightness) }
//Main SetLED Function requires all paramaters
List<String> setLED(ledNumber, String colorName, brightness) {
   if (enableDebug) log.debug "setLED(Number $ledNumber, Color $colorName, Brightness $brightness)"
   Integer intLedNum = ledNumber as Integer
   Integer intColor = colorName ? (colorNameMap.find{ colorName.equalsIgnoreCase(it.value) }?.key) : null
   Integer intLevel = null
   switch (brightness as Integer) {
      case 1..44:
         intLevel = 2  // actual Z-Wave value (30%)
         break
      case 45..74:
         intLevel = 1  // actual Z-Wave value (60%)
         break
      case 75..100:
         intLevel = 0  // actual Z-Wave value (100%)
         break
      case 0:
         intLevel = -1  // using to mean "off"
         break
   }
   if (enableDebug) log.debug "setLED(Number $intLedNum, Color $intColor, Brightness $intLevel) ADJUSTED"
   List<String> cmds = []
   if (intLevel == -1) {
      // Set LED to "always off" (may want to change in future if add association), unless #5/relay and configured not to:
      if (intLedNum != 5 || (relayLEDBehavior as Integer) == 4) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 2, parameterNumber: ledIndicatorParams[intLedNum], size: 1))
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: ledIndicatorParams[intLedNum]))
      }
      else {
         log.warn "Relay LED (#5) not configured to allow turning off from setLED (no changes made)"
      }
   }
   else {
      if (intColor >= 0) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: ledColorParams[intLedNum], size: 1))
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: ledColorParams[intLedNum]))
      }
      if (intLevel >= 0) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: ledBrightnessParams[intLedNum], size: 1))
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: ledBrightnessParams[intLedNum]))
      }
      // Set LED to "always on" (may want to change in future if add association):
      if (intLedNum != 5 || (relayLEDBehavior as Integer) == 4) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 3, parameterNumber: ledIndicatorParams[intLedNum], size: 1))
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: ledIndicatorParams[intLedNum]))
      }
   }
   return cmds ? delayBetween(cmds, 300) : []
}

List<String> setIndicator(Number ledNumber=0, String mode="on", Number lengthOfOnOffPeriods=null, Number numberOfOnOffPeriods=null, Number lengthOfOnPeriod=null) {
   if (enableDebug) log.debug "setIndicator($ledNumber, $mode, $lengthOfOnOffPeriods, $numberOfOnOffPeriods, $lengthOfOnPeriod)"
   Short indId = indicatorLEDNumberMap[ledNumber as Integer] ?: 0
   List<String> cmds = []
   if (mode.equalsIgnoreCase("flash")) {
      Short lenOnOff = lengthOfOnOffPeriods != null ? lengthOfOnOffPeriods as Short : 0
      Short numOnOff = numberOfOnOffPeriods != null ? numberOfOnOffPeriods as Short : 0
      Short lenOn = lengthOfOnPeriod != null ? lengthOfOnPeriod as Short : 0
      if (enableDebug) log.debug "lenOnOff = $lenOnOff, numOnOff=$numOnOff, lenOn=$lenOn, indId=$indId"
      cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value: 0xFF, indicatorCount: 3, indicatorValues: [
            [indicatorId: indId, propertyId: 0x03, value: lenOnOff], // This property is used to set the duration (in tenth of seconds) of an on/off period
            [indicatorId: indId, propertyId: 0x04, value: numOnOff], // This property is used to set the number of on/off periods to run
            [indicatorId: indId, propertyId: 0x05, value: lenOn] // This property is used to set the length of the on time during an on/off period; it allows asymmetric on/off periods
         ]))
   }
   else {
      Short onOff = (mode.equalsIgnoreCase("on") ? 0xFF : 0x00)
      cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value: 0xFF, indicatorCount: 1, indicatorValues: [
            [indicatorId: indId, propertyId: 0x02, value: onOff]
         ]))
   }

   return delayBetween(cmds, 300)
}

// Custom command (for apps/users)
String setConfigParameter(number, value, size) {
   return zwaveSecureEncap(setParameter(number, value, size.toInteger()))
}

// For internal/driver use
String setParameter(number, value, size) {
   if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)"
   return zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number, size: size))
}
