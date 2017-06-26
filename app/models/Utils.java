package models;

import play.Logger;
import java.security.SecureRandom;
import java.util.*;

public final class Utils {

	private static Random random = new SecureRandom();
	private static final char[] CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

	private Utils() {}

	public static String generateUniqueVoterCode() {
		//Warning: this code might iterate more than once and
		//be the reason for bottleneck during poll creation
		String code = generateRandomString(5);
		Voter voter = Voter.find.where().eq("code", code).findUnique();
		while(voter != null) {
			Logger.debug("Code generation collision!");
			code = generateRandomString(5);
			voter = Voter.find.where().eq("code", code).findUnique();
		}
		return code;
	}

	public static String generateUniquePollCode() {
		//Warning: this code might iterate more than once and
		//be the reason for bottleneck during poll creation
		String code = generateRandomString(5);
		Poll poll = Poll.find.where().eq("code", code).findUnique();
		while(poll != null) {
			Logger.debug("Code generation collision!");
			code = generateRandomString(5);
			poll = Poll.find.where().eq("code", code).findUnique();
		}
		return code;
	}

	public static String generateUniqueOptionCode() {
		//Warning: this code might iterate more than once and
		//be the reason for bottleneck during option creation
		String code = generateRandomString(5);
		Option option = Option.find.where().eq("code", code).findUnique();
		while(option != null) {
			Logger.debug("Code generation collision!");
			code = generateRandomString(5);
			option = Option.find.where().eq("code", code).findUnique();
		}
		return code;
	}

	public static String generateUniqueParticipationCode() {
		//Warning: this code might iterate more than once and
		//be the reason for bottleneck during option creation
		String code = generateRandomString(5);
		Participation participation = Participation.find.where().eq("code", code).findUnique();
		while(participation != null) {
			Logger.debug("Code generation collision!");
			code = generateRandomString(5);
			participation = Participation.find.where().eq("code", code).findUnique();
		}
		return code;
	}

	public static String generateUniqueVoteCode() {
		//Warning: this code might iterate more than once and
		//be the reason for bottleneck during option creation
		String code = generateRandomString(5);
		Vote vote = Vote.find.where().eq("code", code).findUnique();
		while(vote != null) {
			Logger.debug("Code generation collision!");
			code = generateRandomString(5);
			vote = Vote.find.where().eq("code", code).findUnique();
		}
		return code;
	}

	private static String generateRandomString(int stringLength) {
		char[] result = new char[stringLength];
		for(int i = 0; i < result.length; i++) {
			int randomCharIndex = random.nextInt(CHARSET.length);
			result[i] = CHARSET[randomCharIndex];
		}
		return new String(result);
	}
}