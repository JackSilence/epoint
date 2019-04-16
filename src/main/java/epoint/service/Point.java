package epoint.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import epoint.util.Utils;
import io.github.bonigarcia.wdm.WebDriverManager;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class Point implements IService {
	private final Logger log = LoggerFactory.getLogger( this.getClass() );

	private static final String TEMPLATE = "/epoint/template/template.html", ROW = "/epoint/template/row.html";

	private static final String DATA_URI = "data:image/png;base64,%s", LOCAL_DATA_PATH = "/src/main/resources/tesseract/";

	@Autowired
	private IMailService service;

	@Value( "${treemall.account}" )
	private String account;

	@Value( "${treemall.password}" )
	private String password;

	@Value( "${GOOGLE_CHROME_SHIM:}" )
	private String bin;

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
		WebDriver driver = init();

		// Heroku上用mobileEmulation或設定手機的user-agent會有問題... 所以才這樣設定連結
		driver.get( "https://www.treemall.com.tw/casso/login?service=https://m.treemall.com.tw/member/pointlist" );

		find( driver, "#signin-account" ).sendKeys( Utils.decode( account ) );
		find( driver, "#signin-password" ).sendKeys( Utils.decode( password ) );

		( ( JavascriptExecutor ) driver ).executeScript( "$('.cd-form img').css('padding-left', '0');" );

		WebElement element = find( driver, "img.inline" );

		File screenshot = ( ( TakesScreenshot ) driver ).getScreenshotAs( OutputType.FILE );

		org.openqa.selenium.Point point = element.getLocation();

		Dimension size = element.getSize();

		int x = point.getX(), y = point.getY(), width = size.getWidth(), height = size.getHeight();

		StringBuilder sb = new StringBuilder();

		String row = Utils.getResourceAsString( ROW ), before = StringUtils.EMPTY, after = StringUtils.EMPTY, code;

		try {
			// 模擬成iphoneX的話, 四個數字都要 * 3
			BufferedImage image = ImageIO.read( screenshot ).getSubimage( x, y, width, height );

			before = file( image );

			IntStream.range( 0, image.getWidth() ).forEach( i -> IntStream.range( 0, image.getHeight() ).forEach( j -> {
				Color color = new Color( image.getRGB( i, j ) );

				int avg = ( color.getRed() + color.getGreen() + color.getBlue() ) / 3;

				image.setRGB( i, j, ( avg <= 30 ? Color.BLACK : Color.WHITE ).getRGB() );

			} ) );

			after = file( image );

			Tesseract tesseract = new Tesseract();

			if ( bin.isEmpty() ) {
				// 本機才用resources底下的, server上看TESSDATA_PREFIX
				tesseract.setDatapath( System.getProperty( "user.dir" ) + LOCAL_DATA_PATH );

			}

			sb.append( String.format( row, "驗證碼", code = StringUtils.remove( tesseract.doOCR( image ), StringUtils.SPACE ) ) );

			find( driver, "#signin-captcha" ).sendKeys( code );

			find( driver, "#btnLogin" ).click();

			sleep();

			driver.findElements( By.cssSelector( "div.point-detail > ul.user-point" ) ).forEach( i -> {
				String[] result = StringUtils.split( i.getText().trim(), "：" );

				if ( ArrayUtils.getLength( result ) == 2 ) {
					sb.append( String.format( row, result[ 0 ], result[ 1 ].replaceAll( "[^\\d]", "" ) ) );

				}
			} );

		} catch ( IOException | TesseractException | NoSuchElementException | UnhandledAlertException e ) {
			log.error( "", e );

		}

		driver.quit();

		String time = new SimpleDateFormat( "yyyyMMddHH" ).format( new Date() );

		service.send( "epoint_" + time, String.format( Utils.getResourceAsString( TEMPLATE ), before, after, sb.toString() ) );
	}

	private WebDriver init() {
		ChromeOptions options = new ChromeOptions();

		if ( bin.isEmpty() ) {
			WebDriverManager.chromedriver().setup();

		} else {
			System.setProperty( "webdriver.chrome.driver", "/app/.chromedriver/bin/chromedriver" );

			options.setBinary( bin );

		}

		options.addArguments( "--headless", "--disable-gpu" );

		return new ChromeDriver( options );
	}

	private WebElement find( WebDriver driver, String css ) {
		return driver.findElement( By.cssSelector( css ) );
	}

	private String file( BufferedImage image ) {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			ImageIO.write( image, "png", stream );

			return String.format( DATA_URI, DatatypeConverter.printBase64Binary( stream.toByteArray() ) );

		} catch ( IOException e ) {
			throw new RuntimeException( e );

		}
	}

	private void sleep() {
		try {
			Thread.sleep( 5000 );

		} catch ( InterruptedException e ) {
			throw new RuntimeException( e );

		}
	}

}