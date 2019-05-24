package epoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import magic.controller.ExecuteController;
import magic.service.AsyncExecutor;
import magic.service.SendGrid;
import magic.service.Slack;

@SpringBootApplication
@EnableScheduling
@Import( { ExecuteController.class, SendGrid.class, AsyncExecutor.class, Slack.class } )
public class App {
	public static void main( String[] args ) {
		SpringApplication.run( App.class, args );
	}
}