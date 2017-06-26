package models;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.mvc.*;
import java.util.concurrent.*;

public class SecuredAction extends Action.Simple { 
	public CompletionStage<Result> call(Http.Context ctx) {
		ObjectNode jsonResponseBody = play.libs.Json.newObject();
		ArrayNode errors = play.libs.Json.newArray();
		final String userCode;
		final String accessToken = extractToken(ctx);

		if(accessToken == null) {
			requestMissingToken(errors);
			jsonResponseBody.set("errors", errors);
			return CompletableFuture.completedFuture(unauthorized(jsonResponseBody));
		}
		userCode = Token.confirmAccessToken(accessToken);
		if(userCode == null) {
			requestInvalidToken(errors);
			jsonResponseBody.set("errors", errors);
			return CompletableFuture.completedFuture(unauthorized(jsonResponseBody));
		}

		final Voter loggedInUser = Voter.findVoterByCode(userCode);
		//Token is valid but user does not exist in database(hard delete).
		if(loggedInUser == null) {
			requestInvalidToken(errors);
			jsonResponseBody.set("errors", errors);
			return CompletableFuture.completedFuture(unauthorized(jsonResponseBody));
		}
		//Save user within context args for easier access.
		ctx.args.put("user", loggedInUser);
		return delegate.call(ctx);
    }
	
	private String extractToken(Http.Context ctx) {
		//Example header Authorization: "bearer <token>".
		String[] authorizationHeader = ctx.request().getHeader("Authorization").split("\\s+");
		if(authorizationHeader[0] == null || !authorizationHeader[0].equals("bearer") || authorizationHeader[1] == null) {
			return null;
		}
		return authorizationHeader[1];
	}

	private void requestMissingToken(ArrayNode errorsArray) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", "401");
		error.put("code", "5");
		error.put("message", "Unauthorized. Token is missing.");
		error.put("detail", "The request is missing an authentication token.");
		error.put("moreInfo", "https://localhost:9000/blablabla");
		errorsArray.add(error);
	}
	private void requestInvalidToken(ArrayNode errorsArray) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", "401");
		error.put("code", "5");
		error.put("message", "Unauthorized. Token is invalid.");
		error.put("detail", "The token provided is invalid. Please re-authenticate");
		error.put("moreInfo", "https://localhost:9000/blablabla");
		errorsArray.add(error);
	}
}