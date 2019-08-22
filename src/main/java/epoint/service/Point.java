package epoint.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import epoint.model.Result;
import magic.service.IMailService;
import magic.service.Selenium;
import magic.util.Utils;
import net.gpedro.integrations.slack.SlackAttachment;
import net.gpedro.integrations.slack.SlackMessage;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class Point extends Selenium {
	private static final int START_COUNT = 1, MAX_RETRY = 10;

	private static final String TEMPLATE = "/epoint/template/template.html", ROW = "/epoint/template/row.html";

	private static final String LOCAL_DATA_PATH = "/src/main/resources/tesseract/";

	@Autowired
	private IMailService service;

	@Value( "${point.url}" )
	private String url;

	@Value( "${point.account}" )
	private String account;

	@Value( "${point.password}" )
	private String password;

	static {
		// Buildpacks:
		// heroku/gradle
		// https://github.com/heroku/heroku-buildpack-apt
		// https://github.com/heroku/heroku-buildpack-chromedriver
		// https://github.com/heroku/heroku-buildpack-google-chrome
		System.setProperty( "jna.library.path", "/app/.apt/usr/lib/x86_64-linux-gnu" );
	}

	@Override
	@Scheduled( cron = "0 0 11,18 * * *" )
	public void exec() {
		run();
	}

	@Override
	protected void run( WebDriver driver ) {
		// Heroku上用mobileEmulation或設定手機的user-agent會有問題... 所以才這樣設定連結
		driver.get( url );

		Result result = new Result();

		try {
			find( driver, "#signin-account" ).sendKeys( Utils.decode( account ) );

			handle( driver, result, START_COUNT );

		} catch ( IOException | TesseractException | NoSuchElementException e ) {
			throw new RuntimeException( e );

		}

		String before = result.getBefore(), after = result.getAfter(), text = result.getText(), message = result.getMessage(), subject;

		service.send( subject = Utils.subject( "點數查詢" ), String.format( Utils.getResourceAsString( TEMPLATE ), before, after, text ) );

		slack.call( new SlackMessage( "" ).addAttachments( new SlackAttachment( message ).setText( message ).setPretext( subject ) ) );
	}

	private void handle( WebDriver driver, Result result, int count ) throws IOException, TesseractException {
		String row = Utils.getResourceAsString( ROW ), code;

		find( driver, "#signin-password" ).sendKeys( Utils.decode( password ) );

		script( driver, "$('.cd-form img').css('padding-left', '0');" );

		BufferedImage image = screenshot( driver, find( driver, "img.inline" ) );

		result.setBefore( base64( image ) );

		IntStream.range( 0, image.getWidth() ).forEach( i -> IntStream.range( 0, image.getHeight() ).forEach( j -> {
			Color color = new Color( image.getRGB( i, j ) );

			int avg = ( color.getRed() + color.getGreen() + color.getBlue() ) / 3;

			image.setRGB( i, j, ( avg <= 30 ? Color.BLACK : Color.WHITE ).getRGB() );

		} ) );

		result.setAfter( base64( image ) );

		Tesseract tesseract = new Tesseract();

		if ( bin.isEmpty() ) {
			// 本機才用resources底下的, server上看TESSDATA_PREFIX
			tesseract.setDatapath( System.getProperty( "user.dir" ) + LOCAL_DATA_PATH );
		}

		result.setText( String.format( row, "驗證碼", code = StringUtils.remove( tesseract.doOCR( image ), StringUtils.SPACE ) ) );

		try {
			find( driver, "#signin-captcha" ).sendKeys( code );

			find( driver, "#btnLogin" ).click();

			sleep();

			StringBuilder sb = new StringBuilder();

			list( driver, "div.point-detail > ul.user-point" ).forEach( i -> {
				String text = i.getText();

				sb.append( text ).append( "\n" );

				String[] data = StringUtils.split( text.trim(), "：" );

				if ( ArrayUtils.getLength( data ) == 2 ) {
					result.setText( result.getText() + String.format( row, data[ 0 ], data[ 1 ].replaceAll( "[^\\d]", "" ) ) );

				}
			} );

			result.setMessage( sb.toString() );

		} catch ( UnhandledAlertException e ) {
			Alert alert = driver.switchTo().alert();

			log.info( "Alert data: {}, count: {}", alert.getText(), count );

			alert.accept();

			if ( count >= MAX_RETRY ) {
				log.info( "Max retry count exceeded!" );

				return;

			}

			handle( driver, result, ++count );

		}
	}
}