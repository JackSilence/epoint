package epoint.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

@Service
public class MailService implements IMailService {
	private final Logger log = LoggerFactory.getLogger( this.getClass() );

	@Value( "${sendgrid.api.key:}" )
	private String key;

	@Override
	public void send( String subject, String content ) {
		try {
			Email from = new Email( "notice@heroku.com" ), to = new Email( "lethington@gmail.com" );

			Request request = new Request();

			request.setMethod( Method.POST );
			request.setEndpoint( "mail/send" );
			request.setBody( new Mail( from, subject, to, new Content( "text/html", content ) ).build() );

			Response response = new SendGrid( key ).api( request );

			log.info( "Status: {}", response.getStatusCode() );

		} catch ( IOException e ) {
			throw new RuntimeException( "Failed to send (SendGrid): " + subject, e );

		}
	}
}