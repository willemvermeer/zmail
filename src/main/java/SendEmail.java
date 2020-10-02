import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class SendEmail
{
	public static void main(String [] args){
		String to = "sonoojaiswal1988@gmail.com";//change accordingly
		String from = "sonoojaiswal1987@gmail.com";//change accordingly
		String host = "127.0.0.1";//or IP address
		String port = "8123";

		//Get the session object
		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host", host);
		properties.setProperty("mail.smtp.port", port);
		Session session = Session.getDefaultInstance(properties);

		for (int i=0; i<5; i++) {
			//compose the message
			try {
				MimeMessage message = new MimeMessage(session);
				message.setFrom(new InternetAddress(from));
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
				message.setSubject("Ping " + i);
				message.setText("Hello, this is example of sending email  ");

				// Send message
				System.out.println("About to send message " + i);
				Transport.send(message);
				System.out.println("message sent successfully....");

			} catch (MessagingException mex) {
				mex.printStackTrace();
			}
		}
	}
}