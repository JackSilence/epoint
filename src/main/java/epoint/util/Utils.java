package epoint.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.io.IOUtils;

public class Utils {
	public static String decode( String str ) {
		return new String( Base64.getDecoder().decode( str ) );
	}

	public static String getResourceAsString( String path ) {
		try {
			return IOUtils.toString( Utils.class.getResource( path ), StandardCharsets.UTF_8.name() );

		} catch ( IOException e ) {
			throw new RuntimeException( "Path: " + path, e );
		}
	}
}