/*
 * Leviton Decora Z-Wave Plus Dimmer for Hubitat Elevation
 * Author: Joe Roback <joe.roback@gmail.com>
 *
 * ChangeLog
 *
 *   v1.0.0 - 2019-11-12 - jroback
 *     - initial release, only tested with DZ6HD models with v1.20 firmware
 *
 * MIT License
 *
 * Copyright (c) 2019 Joe Roback
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

metadata {
    definition (
        name: "Leviton Decora Z-Wave Plus Dimmer",
        namespace: "jroback",
        author: "Joe Roback",
        importUrl: "https://raw.githubusercontent.com/joeroback/hubitat/master/drivers/leviton-zwave-plus-dimmer.groovy"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "Switch Level"
        capability "ChangeLevel"
        capability "Configuration"

        command "flash"
        command "refresh"

        attribute "fadeOnTime", "number"
        attribute "fadeOffTime", "number"
        attribute "minLevel", "number"
        attribute "maxLevel", "number"
        attribute "presetLevel", "number"
        attribute "levelIndicatorTimeout", "number"
        attribute "indicatorStatus", "enum", ["Never", "When On", "When Off", "Unknown"]
        attribute "loadType", "enum", ["Incandescent", "LED", "CFL", "Unknown"]
        attribute "firmwareVersion", "string"
        attribute "zwaveProtocol", "string"

        fingerprint mfr: "001D", prod: "3201", deviceId: "0001", inClusters: "0x5E,0x85,0x59,0x86,0x72,0x70,0x5A,0x73,0x26,0x20,0x27,0x2C,0x2B,0x7A", outClusters: "0x82", deviceJoinName: "Leviton Decora Z-Wave Plus Dimmer (DZ6HD)"
        fingerprint mfr: "001D", prod: "3301", deviceId: "0001", inClusters: "0x5E,0x85,0x59,0x86,0x72,0x70,0x5A,0x73,0x26,0x20,0x27,0x2C,0x2B,0x7A", outClusters: "0x82", deviceJoinName: "Leviton Decora Z-Wave Plus Dimmer (DZ1KD)"
    }

    preferences {
        input name: "fadeOnTime", type: "number", title: "Fade On Time", description: "0=Instant On, 1-127=1-127 seconds, 128-253=1-126 minutes", defaultValue: 2, range: "0..253", required: true, displayDuringSetup: false
        input name: "fadeOffTime", type: "number", title: "Fade Off Time", description: "0=Instant Off, 1-127=1-127 seconds, 128-253=1-126 minutes", defaultValue: 2, range: "0..253", required: true, displayDuringSetup: false
        input name: "minLevel", type: "number", title: "Minimum Light Level", defaultValue: 10, range: "0..100", required: true, displayDuringSetup: false
        input name: "maxLevel", type: "number", title: "Maximum Light Level", defaultValue: 100, range: "0..100", required: true, displayDuringSetup: false
        input name: "presetLevel", type: "number", title: "Preset Light Level", description: "(0=Last Dim State, 1-100 level)", defaultValue: 0, range: "0..100", required: true, displayDuringSetup: false
        input name: "levelIndicatorTimeout", type: "number", title: "LED  Dim Level Indicator Timeout", description: "(0=Level Indicators Off, 1-254 seconds, 255=Always On)", defaultValue: 3, range: "0..255", required: true, displayDuringSetup: false
        input name: "indicatorStatus", type: "enum", title: "Locator LED Status", options:[["0": "Never"], ["254": "When On"], ["255": "When Off"]], defaultValue: "255", required: true, displayDuringSetup: false
        input name: "loadType", type: "enum", title: "Load Type", options:[["0": "Incandescent"], ["1": "LED"], ["2": "CFL"]], defaultValue: "0", required: true, displayDuringSetup: false
        input name: "flashRate", type: "enum", title: "Flash rate", options: [["750": "750ms"], ["1000": "1s"], ["2000": "2s"], ["5000": "5s"]], defaultValue: "1000"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "traceEnable", type: "bool", title: "Enable debug traces", defaultValue: false
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    device.updateSetting("traceEnable", [value:"false", type:"bool"])
}

def logInfo(msg) {
    log.info "${device.getDisplayName()} id: ${device.getId()} state: ${state} -- " + msg
}

def logWarn(msg) {
    log.warn "${device.getDisplayName()} id: ${device.getId()} state: ${state} -- " + msg
}

def logDebug(msg) {
    if (logEnable) log.debug "${device.getDisplayName()} id: ${device.getId()} state: ${state} -- " + msg
}

def logTrace(msg) {
    if (traceEnable) log.trace "${device.getDisplayName()} id: ${device.getId()} state: ${state} -- " + msg
}

def clampLevel(level) {
    level > 99 ? 99 : (level < 0 ? 0 : level)
}

def command(hubitat.zwave.Command cmd) {
    cmd.format()
}

def commands(cmds) {
    cmds.collect { it instanceof String ? it : command(it) }
}

def delayCommands(cmds, delay = 150) {
    delayBetween(commands(cmds), delay)
}

def parse(String description) {
    logTrace "parse description: ${description}"

    def result = null
    def cmd = zwave.parse(description)
    logDebug "parse cmd: ${cmd}"

    if (cmd) {
        result = zwaveEvent(cmd)
    }

    return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logInfo "BasicReport: ${cmd}"

    def type
    def levelValue = clampLevel(cmd.value)
    def switchValue = levelValue == 0 ? "off" : "on"

    switch (state.bin) {
        case -1:
            type = "physical"
            break
        case -2:
        case -10:
        case -11:
        case 0..99:
            type = "digital"
            break
        default:
            logDebug "Unknown state!!!"
            break
    }

    state.bin = -1

    def events = [
        sendEvent(name: "switch", value: switchValue, type: type)
    ]

    if (levelValue > 0) {
        events << sendEvent(name: "level", value: levelValue, type: type, unit: "%")
    }

    return events
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    [
        sendEvent(name: "firmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}"),
        sendEvent(name: "zwaveProtocol", value: "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    ]
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    logDebug "manufacturerId:   ${cmd.manufacturerId}"
    logDebug "manufacturerName: ${cmd.manufacturerName}"
    logDebug "productId:        ${cmd.productId}"
    if (cmd.productTypeId == 0x3201) {
        updateDataValue("model", "DZ6HD")
    }
    else if (cmd.productTypeId == 0x3301) {
        updateDataValue("model", "DZ1KD")
    }
    updateDataValue("manufacturer", cmd.manufacturerName)
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    def name
    def value
    def reportValue = cmd.configurationValue[0].toInteger()

    switch (cmd.parameterNumber) {
        case 1:
            name = "fadeOnTime"
            value = reportValue == 0 ? "Instant On" : reportValue < 128 ? "${reportValue} seconds": "${-> reportValue - 127} minutes"
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 2:
            name = "fadeOffTime"
            value = reportValue == 0 ? "Instant Off" : reportValue < 128 ? "${reportValue} seconds": "${-> reportValue - 127} minutes"
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 3:
            name = "minLevel"
            value = reportValue
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 4:
            name = "maxLevel"
            value = reportValue
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 5:
            name = "presetLevel"
            value = reportValue == 0 ? "Last Dim State" : reportValue
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 6:
            name = "levelIndicatorTimeout"
            value = reportValue == 0 ? "Off" : "${reportValue} seconds"
            device.updateSetting(name, [value: reportValue, type: "number"])
            break
        case 7:
            name = "indicatorStatus"
            value = reportValue == 0 ? "Never" : reportValue == 254 ? "When On" : reportValue == 255 ? "When Off" : "Unknown"
            device.updateSetting(name, [value: reportValue.toString(), type: "enum"])
            break
        case 8:
            name = "loadType"
            value = reportValue == 0 ? "Incandescent" : reportValue == 1 ? "LED" : reportValue == 2 ? "CFL" : "Unknown"
            device.updateSetting(name, [value: reportValue.toString(), type: "enum"])
            break
        default:
            log.warn "Unknown Configuration Command!! Parameter Number: ${cmd.parameterNumber} Value: ${reportValue}"
            break
    }

    sendEvent([name: name, value: value])
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    // ignore deprecated hail command
    if (!(cmd instanceof hubitat.zwave.commands.hailv1.Hail)) {
        logWarn "Unhandled Command: ${cmd}"
    }
}

def on() {
    logDebug "on"
    state.bin = -10
    state.flashing = false
    delayCommands([
        zwave.basicV1.basicSet(value: 0xFF),
        zwave.basicV1.basicGet()
    ])
}

def off() {
    logDebug "off"
    state.bin = -11
    state.flashing = false
    delayCommands([
        zwave.basicV1.basicSet(value: 0x00),
        zwave.basicV1.basicGet()
    ])
}

def setLevel(value) {
    setLevel(value, 1)
}

def setLevel(value, duration) {
    logDebug "setLevel(${value}, ${duration})"

    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
    def level = clampLevel(value)

    state.bin = level
    state.flashing = false

    delayCommands([
        zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration),
        zwave.basicV1.basicGet()
    ])
}

def startLevelChange(direction) {
    logDebug "startLevelChange(${direction})"
    def upDown = direction == "down" ? 1 : 0
    delayCommands([
        zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0),
        zwave.basicV1.basicGet()
    ])
}

def stopLevelChange() {
    logDebug "stopLevelChange"
    delayCommands([
        zwave.switchMultilevelV1.switchMultilevelStopLevelChange(),
        zwave.basicV1.basicGet()
    ])
}

def flash() {
    logDebug "flash with a rate of ${flashRate} milliseconds"
    state.flashing = true
    flashOn()
}

def flashOn() {
    logTrace "flashOn"
    if (!state.flashing) return
    runInMillis(flashRate.toInteger(), flashOff)
    delayCommands([
        zwave.basicV1.basicSet(value: 0xFF),
        zwave.basicV1.basicGet()
    ])
}

def flashOff() {
    logTrace "flashOff"
    if (!state.flashing) return
    runInMillis(flashRate.toInteger(), flashOn)
    delayCommands([
        zwave.basicV1.basicSet(value: 0x00),
        zwave.basicV1.basicGet()
    ])
}

def refresh() {
    logDebug "refresh"
    state.bin = -2

    def cmds = []

    if (getDataValue("model") == null) {
        cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    }

    cmds << zwave.versionV1.versionGet()

    for (i in 1..8) {
        cmds << zwave.configurationV1.configurationGet(parameterNumber: i)
    }

    cmds << zwave.basicV1.basicGet()

    delayCommands(cmds, 100)
}

def installed() {
    logDebug "installed"
}

def configure() {
    logDebug "configure"
    state.bin = -1
    state.flashing = false
    if (logEnable || traceEnable) runIn(1800, logsOff)
    refresh()
}

def updated() {
    logDebug "updated"
    if (logEnable || traceEnable) runIn(1800, logsOff)

    def setCmds = []
    def getCmds = []

    if (fadeOnTime) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: fadeOnTime.toInteger(), parameterNumber: 1, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    }
    if (fadeOffTime) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: fadeOffTime.toInteger(), parameterNumber: 2, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 2)
    }
    if (minLevel) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: minLevel.toInteger(), parameterNumber: 3, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
    }
    if (maxLevel) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: maxLevel.toInteger(), parameterNumber: 4, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 4)
    }
    if (presetLevel) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: presetLevel.toInteger(), parameterNumber: 5, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 5)
    }
    if (levelIndicatorTimeout) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: levelIndicatorTimeout.toInteger(), parameterNumber: 6, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 6)
    }
    if (indicatorStatus) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: indicatorStatus.toInteger(), parameterNumber: 7, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 7)
    }
    if (loadType) {
        setCmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: loadType.toInteger(), parameterNumber: 8, size: 1)
        getCmds << zwave.configurationV1.configurationGet(parameterNumber: 8)
    }

    getCmds << zwave.basicV1.basicGet()

    if (setCmds) {
        delayCommands(setCmds + getCmds, 100)
    }
}
