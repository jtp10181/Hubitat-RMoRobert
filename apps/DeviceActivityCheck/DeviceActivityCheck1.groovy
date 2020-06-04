/**
 * ==========================  Device Activity Check ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2020 Robert Morris
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
 *
 *  Author: Robert Morris
 *
 * == App version: 1.0.1 ==
 *
 * Changelog:
 * 1.0.1 (2020-06-04) - Minor bugfix (eliminates errors for empty groups or if notification device is not selected)
 * 1.0   (2020-05-27) - First public release
 *
 */

 import groovy.transform.Field

@Field static List dateFormatOptions = ['MMM d, yyyy, h:mm a', 'E, MMM d, yyyy, h:mm a', 'E dd MMM yyyy, h:mm a', 'dd MMM yyyy, h:mm a', ,
                                        'dd MMM yyyy HH:mm', 'E MMM dd HH:mm', 'yyyy-MM-dd HH:mm z']
@Field static Integer formatListIfMoreItemsThan = 4

definition(
    name: "Device Activity Check",
    namespace: "RMoRobert",
    author: "Robert Morris",
    description: "Identify devices without recent activity that may have stopped working or \"fallen off\" your network",
	category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    documentationLink: "https://community.hubitat.com/t/release-device-activity-check-get-notifications-for-inactive-devices/42176"
)

preferences {
	page(name: "pageMain")
	page(name: "pageDeviceGroup")
	page(name: "pageRemoveGroup")
	page(name: "pageViewReport")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Device Activity Check", uninstall: true, install: true) {
		if (!(state.groups)) state.groups = [1]
		List groups = state.groups ?: [1]
		if (state.removeSettingsForGroupNumber) { 
			Integer groupNum = state.removeSettingsForGroupNumber
			state.remove('removeSettingsForGroupNumber')
			removeSettingsForGroupNumber(groupNum)
			state.groups?.removeElement(groupNum)
		}
		state.remove('cancelDelete')
		state.remove('lastGroupNum')
		
		String strSectionTitle = (state.groups.size() > 1) ? "Device Groups" : "Devices"
		section(styleSection(strSectionTitle)) {
			groups.eachWithIndex { realGroupNum, groupIndex ->
				String timeout = getDeviceGroupInactivityThresholdString(realGroupNum)
				String strTitle = (state.groups.size() > 1) ? "Group ${groupIndex+1} Devices (inactivity threshold: $timeout):" : "Devices (inactivity threshold: $timeout):"
				href(name: "pageDeviceGroup${realGroupNum}Href",
				     page: "pageDeviceGroup",
					 params: [groupNumber: realGroupNum],
					 title: strTitle,
					 description: getDeviceGroupDescription(realGroupNum) ?: "Click/tap to choose devices and inactivity threshold...",
					 state: getDeviceGroupDescription(realGroupNum) ? 'complete' : null)
				}
			paragraph("To monitor another set of devices with a different inactiviy threshold, add a new group:")
			input name: "btnNewGroup", type: "button", title: "Add new group"
		}

		section(styleSection("Notification Options")) {
			input name: "notificationDevice", type: "capability.notification", title: "Send notification with list of inactive devices to this device:"
			input name: "notificationTime", type: "time", title: "Daily at this time:"
			paragraph "Or any time this switch is turned on:"
			input name: "notificationSwitch", type: "capability.switch", title: "Switch", description: "Optional - Click to set"
			input name: "includeTime", type: "bool", title: "Include last acitivty time in notifications", defaultValue: true, submitOnChange: true
			if (!settings['includeTime'] == false) {
				List<Map<String,String>> timeFormatOptions = []
				Date currDate = new Date()
				dateFormatOptions.each { 
					timeFormatOptions << ["$it": "${currDate.format(it, location.timeZone)}"]
				}
				input name: "timeFormat", type: "enum", options: timeFormatOptions, title: "Date/time format for notifications:",
					defaultValue: timeFormatOptions[0]?.keySet()[0], required: (!settings['includeTime'] == false), submitOnChange: true
			}
		}

		section(styleSection("View/Test Report")) {
			href(name: "pageViewReportHref",
				 page: "pageViewReport",
				 title: "View current report",
				 description: getDeviceGroupDescription(groupNum) ?: "Evaluate all devices now according to the criteria above, and display a report of \"inactive\" devices.")
			paragraph "The \"Text Notification Now\" button will send a notification to your selected device(s) if there is inactivity to report. This a manual method to trigger the same report the above options would also create:"
			input name: "btnTestNotification", type: "button", title: "Test Notification Now"
		}
		
		section("Advanced Options", hideable: true, hidden: true) {
			label title: "Customize installed app name:", required: true
			input name: "includeHubName", type: "bool", title: "Include hub name in reports (${location.name})"
			input name: "useNotificationTimeFormatForReport", type: "bool", title: 'Use "Date/time format for notifications" for "View current report" dates/times'
			input "modes", "mode", title: "Only send notifications when mode is", multiple: true, required: false
            input name: "debugLogging", type: "bool", title: "Enable debug logging" 
		}
	}
}

def pageDeviceGroup(params) {
	Integer groupNum = params?.groupNumber
	String strTitle = (state.groups.size() > 1) ? "Device Group ${groupNum+1}:" : "Devices"
	if (groupNum) state.lastGroupNum = groupNum
	else groupNum = state.lastGroupNum
	state.remove('cancelDelete')

    dynamicPage(name: "pageDeviceGroup", title: strTitle, uninstall: false, install: false, nextPage: "pageMain") {
		section(styleSection("Choose Devices")) {
			input name: "group${groupNum}.devices", type: "capability.*", multiple: true, title: "Select devices to monitor"
		}
		section(styleSection("Inactivity Threshold")) {
			paragraph "Consider above devices inactive if they have not had activity within..."
			input name: "group${groupNum}.intervalD", type: "number", title: "days",
				description: "", submitOnChange: true, width: 2
			input name: "group${groupNum}.intervalH", type: "number", title: "hours",
				description: "", submitOnChange: true, width: 2
			input name: "group${groupNum}.intervalM", type: "number", title: "minutes*",
				description: "", submitOnChange: true, width: 2
			paragraph """${(settings["group${groupNum}.intervalD"] || settings["group${groupNum}.intervalH"] || settings["group${groupNum}.intervalM"]) ?
				'<strong>Total time:</strong>\n' + daysHoursMinutesToString(settings["group${groupNum}.intervalD"], settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"]) :
				''}""", width: 6
			if (!(settings["group${groupNum}.intervalD"] || settings["group${groupNum}.intervalH"] || settings["group${groupNum}.intervalM"])) {
				paragraph "*At least one of: days, hours, or minutes is required"
			}
		}
		section(styleSection("Remove Group")) {
			href(name: "pageRemoveGroupHref",
				 page: "pageRemoveGroup",
				 title: "Remove this group",
				 params: [deleteGroupNumber: groupNum],
				 description: "Warning: this will delete all selected devices and settings for this group.")
		
			//if (state.groups.size() > 1) input name: "btnRemoveGroup${groupNum}", type: "button", title: "Remove Group"
		}
	}
}

def pageRemoveGroup(params) {
	logDebug("pageRemoveGroup with parameters $params...")
	Integer groupNum = params?.deleteGroupNumber	
	if (groupNum && !(state.cancelDelete)) {
		state.remove('lastGroupNum')
		state.removeSettingsForGroupNumber = groupNum
	}
	dynamicPage(name: "pageRemoveGroup", title: "Remove Group", uninstall: false, install: false, nextPage: "pageMain") {
		section() {
			if (!(state.cancelDelete)) {
				paragraph("Press \"Next\" to complete the deletion of this group.")
				input name: "btnCancelGroupDelete", type: "button", title: "Cancel"
			}
			else {				
				paragraph("Deletion cancelled. Press \"Next\" to continue.")
			}
		}
	}
}

def pageViewReport() {
	dynamicPage(name: "pageViewReport", title: "Device Activity Check", uninstall: false, install: false, nextPage: "pageMain") {
		section(styleSection("Inactive Device Report")) {
			List<com.hubitat.app.DeviceWrapper> inactiveDevices = getInactiveDevices()
			if (inactiveDevices) {
				paragraph "<strong>Device</strong>", width: 6
				paragraph "<strong>Last Activity</strong>", width: 6
				Boolean doFormatting = inactiveDevices.size() > formatListIfMoreItemsThan
				inactiveDevices.eachWithIndex { dev, index ->
					String lastActivity
					if (settings['useNotificationTimeFormatForReport']) {
						lastActivity = dev.getLastActivity()?.format(settings['timeFormat'] ?: 'MMM dd, yyyy h:mm a', location.timeZone)
					}
					else {
						lastActivity = dev.getLastActivity()?.toString()
					}
					if (!lastActivity) lastActivity = 'No reported activity'
					paragraph(doFormatting ? "${styleListItem(dev.displayName, index)}" : "${dev.displayName}", width: 6)
					paragraph(doFormatting ? "${styleListItem(lastActivity, index)}" : lastActivity, width: 6)
				}
			}
			else {
				paragraph "No inactive devices to report"
			}
		}
	}
}


List<com.hubitat.app.DeviceWrapper> getInactiveDevices(Boolean sortByName=true) {
	List<Integer> groups = state.groups ?: [1]
	List<com.hubitat.app.DeviceWrapper> inactiveDevices = []
	Long currEpochTime = now()
	groups.each { groupNum ->
		List allDevices = settings["group${groupNum}.devices"] ?: []
		Integer inactiveMinutes = daysHoursMinutesToMinutes(settings["group${groupNum}.intervalD"],
			settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
		Long cutoffEpochTime = currEpochTime - (inactiveMinutes * 60000)
		allDevices?.each { dev ->
			Boolean isInactive = dev.getLastActivity()?.getTime() <= cutoffEpochTime
		}
		inactiveDevices.addAll(allDevices?.findAll { it.getLastActivity()?.getTime() <= cutoffEpochTime })
	}
	if (sortByName) inactiveDevices = inactiveDevices.sort { it.displayName }
	return inactiveDevices
}


// Lists all devices in group, one per line
String getDeviceGroupDescription(groupNum) {
	String desc = ""
	if (settings["group${groupNum}.devices"]) {
		settings["group${groupNum}.devices"].each { dev ->
			desc += "${dev.displayName}\n"
		}
	}
	return desc
}

// Human-friendly string for inactivity period (e.g., "1 hour, 15 minutes")
String getDeviceGroupInactivityThresholdString(groupNum) {
	return daysHoursMinutesToString(settings["group${groupNum}.intervalD"],
		settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
}

void removeSettingsForGroupNumber(Integer groupNumber) {
	logDebug "Removing settings for group $groupNumber..."
	def settingNamesToRemove = settings?.keySet()?.findAll{ it.startsWith("group${groupNumber}.") }
	logDebug "  Settings to remove: $settingNamesToRemove"
	settingNamesToRemove.each { settingName ->
		app.removeSetting(settingName)
	}
}

Integer daysHoursMinutesToMinutes(days, hours, minutes) {
	Integer totalMin = (minutes ? minutes : 0) + (hours ? hours * 60 : 0) + (days ? days * 1440 : 0)
	return totalMin
}

String daysHoursMinutesToString(days, hours, minutes) {
	Integer totalMin = daysHoursMinutesToMinutes(days, hours, minutes)
	Integer d = totalMin / 1440 as Integer
	Integer h = totalMin % 1440 / 60 as Integer
	Integer m = totalMin % 60
	String strD = "$d day${d != 1 ? 's' : ''}"
	String strH = "$h hour${h != 1 ? 's' : ''}"
	String strM = "$m minute${m != 1 ? 's' : ''}"
	return "${d ? strD : ''}${d && (h || m) ? ', ' : ''}${h ? strH : ''}${(h && m) ? ', ' : ''}${m || !(h || d) ? strM : ''}"
}

String styleSection(String sectionHeadingText) {
	return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

void switchHandler(evt) {
	if (evt.value == 'on') {
		logDebug("Switch turned on; running report")
		sendInactiveNotification()
	}
}

// Sends notification with list of inactive devices to selected notification device(s)
void sendInactiveNotification(Boolean includeLastActivityTime=(settings['includeTime'] != false)) {
	logDebug "sendInactiveNotification() called...preparing list of inactive devices."
	List<com.hubitat.app.DeviceWrapper> inactiveDevices = getInactiveDevices()
	String notificationText = ""
	if (inactiveDevices && isModeOK()) {
		notificationText += (settings['includeHubName'] ? "${app.label} - ${location.name}:" : "${app.label}:")		
		inactiveDevices.each { dev ->
			notificationText += "\n${dev.displayName}"
			if (includeLastActivityTime) {
				String dateString = dev.getLastActivity()?.format(settings['timeFormat'] ?: 'MMM dd, yyyy h:mm a', location.timeZone) ?: 'No activity reported'
				notificationText += " - $dateString"
			}
		}		
		logDebug "Sending notification for inactive devices"
		notificationDevice?.deviceNotification(notificationText)
	}
	else {
		String reason = "Notification skipped: "
		if (inactiveDevices) reason += "No inactive devices. "
		if (!isModeOK()) reason += "Outside of specified mode(s)."
		logDebug reason
	}
}

// List items in report page
String styleListItem(String text, index=0) {
	return """<div style="color: ${index %2 == 0 ? "darkslategray" : "black"}; background-color: ${index %2 == 0 ? 'white' : 'ghostwhite'}">$text</div>"""
}

void scheduleHandler() {
	logDebug("At scheduled; running report")
	sendInactiveNotification()
}


//=========================================================================
// App Methods
//=========================================================================

def installed() {
    log.trace "Installed"
    initialize()
}

def updated() {
    log.trace "Updated"
    unschedule()
    initialize()
}

def initialize() {
	log.trace "Initialized"
	if (settings['debugLogging']) {
		log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
	}

	unsubscribe()
	if (settings['notificationTime']) schedule(settings['notificationTime'], scheduleHandler)	
	if (settings['notificationSwitch']) subscribe(settings['notificationSwitch'], 'switch', switchHandler)
}

Boolean isModeOK() {
    Boolean isOK = !settings['modes'] || settings['modes'].contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${isOK}"
    return isOK
}

def appButtonHandler(btn) {
	switch (btn) {
		case "btnNewGroup":
			Integer newMaxGroup = (state.groups[-1]) ? ((state.groups[-1] as Integer) + 1) : 2
			state.groups << newMaxGroup
			break
		case "btnCancelGroupDelete":
			state.cancelDelete = true
			state.remove('removeSettingsForGroupNumber')
			break
		case "btnTestNotification":
			sendInactiveNotification()
			break
	}
}

/** Writes to log.debug by default if debug logging setting enabled; can specify
  * other log level (e.g., "info") if desired
  */
void logDebug(string, level="debug") {
	if (settings['debugLogging']) {
        log."$level"(string)
    }
}