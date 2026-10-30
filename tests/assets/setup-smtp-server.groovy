import org.jahia.osgi.BundleUtils
import org.jahia.services.mail.MailSettings
import org.jahia.services.mail.MailService
import org.osgi.service.cm.ConfigurationAdmin

// Jahia 8.2.3.8+ and 8.2.4.0+ ship the org.jahia.api.mail API and send mail through the mail-service
// module, where MailService.store() persists nothing.
def mailApiAvailable
try {
    Class.forName("org.jahia.api.mail.MailService", false, MailService.class.classLoader)
    mailApiAvailable = true
} catch (ClassNotFoundException ignored) {
    mailApiAvailable = false
}

if (mailApiAvailable) {
    def props = new Hashtable<String, Object>()
    props.put("smtp.host", "mailpit")
    props.put("smtp.port", "1025")
    props.put("smtp.auth", "false")
    props.put("smtp.starttls", "false")
    props.put("smtp.ssl", "false")
    props.put("default.from", "noreply@jahia.test")
    props.put("default.recipient", "admin@jahia.test")
    props.put("disabled", "false")
    // "?" is a multi-location binding, so the mail-service bundle receives the configuration.
    BundleUtils.getOsgiService(ConfigurationAdmin.class, null)
            .getConfiguration("org.jahia.modules.mail", "?").update(props)
} else {
    MailSettings mailSettings = new MailSettings()
    mailSettings.setServiceActivated(true)
    mailSettings.setUri("smtp://mailpit:1025")
    mailSettings.setFrom("noreply@jahia.test")
    mailSettings.setTo("admin@jahia.test")

    MailService.getInstance().store(mailSettings)
}
