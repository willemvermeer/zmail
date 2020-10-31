import com.sun.mail.smtp.SMTPSendFailedException;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;

public class SendEmail
{
	public static void main(String [] args){
		String to = "willem.vermeer@fizzle.email";//change accordingly
		String from = "sonoojaiswal1987@gmail.com";//change accordingly
		String host = "localhost";//or IP address
		String port = "8125";

		//Get the session object
		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host", host);
		properties.setProperty("mail.smtp.port", port);
		Session session = Session.getDefaultInstance(properties);

		int success = 0;
		for (int i=0; i<1; i++) {
			//compose the message
			try {
				MimeMessage message = new MimeMessage(session);
				message.setFrom(new InternetAddress(from));
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
				message.setSubject("Ping " + i);

				BodyPart messageBodyPart = new MimeBodyPart();
				messageBodyPart.setText("Hello, this is example of sending email  ");

				MimeMultipart multipart = new MimeMultipart();
				multipart.addBodyPart(messageBodyPart);

//				messageBodyPart = new MimeBodyPart();
//				String filename = "/Users/willem/Desktop/image.png";
//				DataSource source = new FileDataSource(filename);
//				messageBodyPart.setDataHandler(new DataHandler(source));
//				messageBodyPart.setFileName(filename);
//				multipart.addBodyPart(messageBodyPart);

				message.setContent(multipart);

				// Send message
				System.out.println("About to send message " + i);
				Transport.send(message);
				System.out.println("message sent successfully....");
				success++;
			} catch (MessagingException mex) {
				mex.printStackTrace();
				System.out.println(((SMTPSendFailedException)mex).getCommand());
				System.out.println(((SMTPSendFailedException)mex).getReturnCode());
			}

			System.out.println("Successes: " + success);
		}
	}
}