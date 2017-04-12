/* **DISCLAIMER**
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
* HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
* Without limitation of the foregoing, Contributors/Regents expressly does not warrant that:
* 1. the software will meet your requirements or expectations;
* 2. the software or the software content will be free of bugs, errors, viruses or other defects;
* 3. any results, output, or data provided through or generated by the software will be accurate, up-to-date, complete or reliable;
* 4. the software will be compatible with third party software;
* 5. any errors in the software will be corrected.
* The user assumes all responsibility for selecting the software and for the results obtained from the use of the software. The user shall bear the entire risk as to the quality and the performance of the software.
*/ 

def clientVersion() {
    return "01.01.00"
}

/**
*  Low Battery Monitor and Notification
*
* Copyright RBoy, redistribution of any changes or code is not allowed without permission
* 2016-11-5 - Added support for automatic code update notifications and fixed an issue with sms
* 2016-10-11 - Initial Release
*/

definition(
    name: "Low Battery Monitor and Notification",
    namespace: "rboy",
    author: "RBoy",
    description: "Monitor devices battery and send notifications when they reach various thresholds",
    category: "Safety & Security",
    iconUrl: "http://smartthings.rboyapps.com/images/BatteryAlert.png",
    iconX2Url: "http://smartthings.rboyapps.com/images/BatteryAlert.png"
)

preferences {
    page(name: "setupAppPage")
    page(name: "newMonitorRulePage")
}


def setupAppPage() {
    log.trace "Settings $settings"

    dynamicPage(name: "setupAppPage", title: "Low Battery Monitor and Notification v${clientVersion()}", install: true, uninstall: true) {    
        if (!atomicState.rules) {
            log.info "Initializing rules"
            atomicState.rules = []
        } else {
            log.trace "MainPage Rules " + atomicState.rules
        }

        section("Battery Monitor Rules") {
            // Sort the list by name
            def rules = atomicState.rules ?: []
            rules.sort{settings."batteryUpper${it.index}"}
            atomicState.rules = rules
            log.info "Sorted rules $atomicState.rules"

            for (rule in atomicState.rules) {
                if (settings."deleteRule${rule.index}" != false) { // If we have marked it false then save it otherwise delete (otherwise it can be null or true, either case we delete it)
                    rules = atomicState.rules
                    rules.remove(rule)
                    atomicState.rules = rules
                    log.info "Deleted rule ${rule.index}"
                    // Now get rid of the rules in the settings
                    deleteSetting("batteryUpper${rule.index}")
                    deleteSetting("monitorDevices${rule.index}")
                    deleteSetting("deleteRule${rule.index}")
                    deleteSetting("name${rule.index}")
                    log.trace "Updated Settings $settings"
                    log.trace "Updated Rules " + atomicState.rules
                } else { // Otherwise show it
                    def hrefParams = [
                        rule: rule,
                        passed: true 
                    ]
                    href(name: "${rule.index}", params: hrefParams, title: "${settings."batteryUpper${rule.index}"}% ${settings."name${rule.index}" ?: ""}", page: "newMonitorRulePage", description: "", required: false)
                }
            }
            def hrefParams = [
                rule: [index:now() as String], // Create a new rule to use (by default we'll delete this rule and settings unless the user confirms), use as String otherwise it won't work on Android
                passed: true 
            ]
            href(name: "NewMonitorRule", params: hrefParams, title: "+ Define a new battery monitor rule", page: "newMonitorRulePage", description: "", required: false)
        }

        section("Notification Options") {
            input "time", "time", title: "Check battery levels at this time everyday", required: true
            input("recipients", "contact", title: "Send notifications to", multiple: true, required: false) {
                paragraph "You can enter multiple phone numbers to send an SMS to by separating them with a '+'. E.g. 5551234567+4447654321"
                input name: "sms", title: "Send SMS notification to (optional):", type: "phone", required: false
                input name: "notify", title: "Send Push Notification", type: "bool", defaultValue: true
            }
        }

        section() {
            label title: "Assign a name for this SmartApp (optional)", required: false
            input name: "disableUpdateNotifications", title: "Don't check for new versions of the app", type: "bool", required: false
        }
    }
}

def newMonitorRulePage(params) {
    //  params is broken, after doing a submitOnChange on this page, params is lost. So as a work around when this page is called with params save it to state and if the page is called with no params we know it's the bug and use the last state instead
    if (params.passed) {
        atomicState.params = params // We got something, so save it otherwise it's a page refresh for submitOnChange
    }

    def rule = []
    // Get user from the passed in params when the page is loading, else get from the last saved to work around not having params on pages
    if (params.rule) {
        rule = params.rule
        log.trace "Passed from main page, using params lookup for rule $rule"
    } else if (atomicState.params) {
        rule = atomicState.params.rule ?: []
        log.trace "Passed from submitOnChange, atomicState lookup for rule $rule"
    } else {
        log.error "Invalid params, no rule found. Params: $params, saved params: $atomicState.params"
    }
    
    log.trace "New Battery Monitor Rule Page, rule:$rule, passed params: $params, saved params:$atomicState.params"

    def existingRule = atomicState.rules.find { it.index == rule.index }
    if (!existingRule) { // If the rule doesn't exist then save it
        def rules = atomicState.rules ?: []
        rules.add(rule)
        atomicState.rules = rules
        log.info "Added rule $rule to $atomicState.rules"
    }

    dynamicPage(name:"newMonitorRulePage", title: "Battery monitor rule", uninstall: false, install: false) {
        section {
            log.trace "RulePage Rules:" + atomicState.rules
            
            def upper = settings."batteryUpper${rule.index}"
            def devices = settings."monitorDevices${rule.index}"
            def name = settings."name${rule.index}"
            def deleteRule = settings."deleteRule${rule.index}"
            
            if (upper && devices) {
                def msg = ""
                // If the settings is `null` then it's not defined and will be deleted automatically otherwise it's been saved
                if (deleteRule == false) {
                    msg = "This rule has been saved"
                } else if (deleteRule == true) {
                    msg = "THIS RULE HAS BEEN DELETED!\nUNCHECK THE DELETE OPTION TO RESTORE THE RULE."
                }
                if (msg) {
                    paragraph msg, required: true
                    log.trace "Saved settings $settings"
                }
            }
            
            log.trace "upper: $upper, name: $name, deleteRule: $deleteRule, devices: $devices"

            input "batteryUpper${rule.index}", "number", title: "If the battery is below (%)", description: "1 to 100", required: true, range: "1..100", submitOnChange: true
            input "monitorDevices${rule.index}", "capability.battery", title: "Monitor these devices", multiple: true, required: true, submitOnChange: true

            if (upper && devices) { // Don't show this for a new rule
                input "name${rule.index}", "text", title: "Name this rule (optional)", required: false, submitOnChange: true
                input "deleteRule${rule.index}", "bool", title: "Delete this rule", required: false, submitOnChange: true
            }
        }
    }
}

def installed() {
    log.trace "Install called with settings $settings"
    initialize()
}

def updated() {
    log.trace "Updated called with settings $settings"
    initialize()
}

def initialize() {
    log.trace "Initializing settings with rules $atomicState.rules"

    unsubscribe()
    unschedule()
    
    TimeZone timeZone = location.timeZone
    if (!timeZone) {
        timeZone = TimeZone.getDefault()
        log.error "Hub timeZone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
        sendPush "Hub timeZone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
    }

    def calendar = Calendar.getInstance()
    calendar.setTimeZone(timeZone)
    def today = calendar.get(Calendar.DAY_OF_WEEK)
    def timeNow = now()
    log.trace("Current time is ${(new Date(timeNow)).format("EEE MMM dd yyyy HH:mm z", timeZone)}")
    log.trace("Battery check schedule ${timeToday(time, timeZone).format("HH:mm z", timeZone)}")
    schedule(timeToday(time, timeZone), checkBatteryLevels)

    // Check for new versions of the code
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    schedule("0 0 " + randomHour + " ? * " + randomDayOfWeek, checkForCodeUpdate) // Check for code updates once a week at a random day and time between 10am and 6pm

    checkBatteryLevels() // Do it now for sanity check
}

def checkBatteryLevels() {
    log.trace "Checking battery levels"
    
    for (rule in atomicState.rules) {
        def upper = settings."batteryUpper${rule.index}" as Integer
        def devices = settings."monitorDevices${rule.index}"
        def name = settings."name${rule.index}"
        
        log.trace "upper: $upper, name: $name, devices: $devices"
        
        for (device in devices) {
            def batteryLevel = device.currentValue("battery") as Integer
            def msg = "${device.displayName} battery level is $batteryLevel%"
			log.trace msg
            if (batteryLevel < upper)
            {
                log.warn "${device.displayName} battery level $batteryLevel% below configured threshold of $upper%"
                sendNotificationMessage(msg)
            }
        }
    }
}

private void sendText(number, message) {
    if (number) {
        def phones = number.split("\\+")
        for (phone in phones) {
            sendSms(phone, message)
        }
    }
}

private void sendNotificationMessage(message) {
    if (location.contactBookEnabled) {
        log.debug "Sending message to $recipients"
        sendNotificationToContacts(message, recipients)
    } else {
        log.debug "SMS: $sms, Push: $notify"
        sms ? sendText(sms, message) : ""
        notify ? sendPush(message) : sendNotificationEvent(message)
    }
}

// Temporarily override the user settings
private updateSetting(name, value) {
    app.updateSetting(name, value) // For SmartApps
    //settings[name] = value // For Device Handlers
}

private deleteSetting(name) {
    app.updateSetting(name, '') // For SmartApps delete it
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy server"
    
    def appName = "Low Battery Monitor and Notification"
    def serverUrl = "http://smartthings.rboyapps.com"
    def serverPath = "/CodeVersions.json"
    
    try {
        httpGet([
            uri: serverUrl,
            path: serverPath
        ]) { ret ->
            log.trace "Received response from RBoyServer, headers=${ret.headers.'Content-Type'}, status=$ret.status"
            //ret.headers.each {
            //    log.trace "${it.name} : ${it.value}"
            //}

            if (ret.data) {
                log.trace "Response>" + ret.data
                
                // Check for app version updates
                def appVersion = ret.data?."$appName"
                if (appVersion > clientVersion()) {
                    def msg = "New version of app ${app.label} available: $appVersion, version: ${clientVersion()}.\nPlease visit $serverUrl to get the latest version."
                    log.info msg
                    if (!disableUpdateNotifications) {
                        sendPush(msg)
                    }
                } else {
                    log.trace "No new app version found, latest version: $appVersion"
                }
                
                // Check device handler version updates
                def caps = []
                for (rule in atomicState.rules) {
                    caps.add(settings."monitorDevices${rule.index}")
                }
                caps?.each {
                    def devices = it?.findAll { it.hasAttribute("codeVersion") }
                    for (device in devices) {
                        if (device) {
                            def deviceName = device?.currentValue("dhName")
                            def deviceVersion = ret.data?."$deviceName"
                            if (deviceVersion && (deviceVersion > device?.currentValue("codeVersion"))) {
                                def msg = "New version of device ${device?.displayName} available: $deviceVersion, version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
                                log.info msg
                                if (!disableUpdateNotifications) {
                                    sendPush(msg)
                                }
                            } else {
                                log.trace "No new device version found for $deviceName, latest version: $deviceVersion, current version: ${device?.currentValue("codeVersion")}"
                            }
                        }
                    }
                }
            } else {
                log.error "No response to query"
            }
        }
    } catch (e) {
        log.error "Exception while querying latest app version: $e"
    }
}