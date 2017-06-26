package models;

import play.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import io.jsonwebtoken.*;

public class Token {

	private static final String SECRET_KEY_BASE64_ENCODED = "c2VjcmV0a2V5Y2hhbmdlbWVwbGVhc2U=";
	
	public static String createAccessToken(Voter user) {
		//Generate monthly token.
		String jwtStr = Jwts.builder()
			.setIssuer("VoteApp")
			.setSubject(user.code)
			.setIssuedAt(Date.from(Instant.now()))
			.setExpiration(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
			.claim("role", user.role)
			.signWith(SignatureAlgorithm.HS256, SECRET_KEY_BASE64_ENCODED).compact();
		//Save access token.
		user.accessToken = jwtStr;
		user.update();
		return jwtStr;
	}

	public static String confirmAccessToken(String accessToken) {
		try {
			Jws<Claims> claims = Jwts.parser()
				.requireIssuer("VoteApp")
				.require("role", "User")
				.setSigningKey(SECRET_KEY_BASE64_ENCODED)
				.parseClaimsJws(accessToken);
			return claims.getBody().getSubject();
		} catch (MissingClaimException e) {
			Logger.debug("Required claim not present: " + e.getClaimName());
			return null;
		} catch (IncorrectClaimException e) {
			Logger.debug("Required claim is wrong: " + e.getClaimName());
			return null;
		} catch (SignatureException e) {
			Logger.debug(e.getMessage());
			return null;
		} catch (ExpiredJwtException e) {
			Logger.debug(e.getMessage());
			return null;
		} catch (MalformedJwtException e) {
			Logger.debug(e.getMessage());
			return null;
		}
	}
}