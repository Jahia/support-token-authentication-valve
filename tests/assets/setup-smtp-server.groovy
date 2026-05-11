import org.jahia.services.mail.MailSettings
import org.jahia.services.mail.MailService

MailSettings mailSettings = new MailSettings()
mailSettings.setServiceActivated(true)
mailSettings.setUri("smtp://mailpit:1025")
mailSettings.setFrom("noreply@jahia.test")
mailSettings.setTo("admin@jahia.test")

MailService.getInstance().store(mailSettings)
