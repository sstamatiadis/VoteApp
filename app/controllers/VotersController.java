package controllers;

import javax.inject.Inject;

import com.avaje.ebean.Ebean;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import org.mindrot.jbcrypt.BCrypt;
import play.Configuration;
import play.Logger;
import play.libs.Json;
import play.mvc.*;
import play.data.*;
import com.fasterxml.jackson.databind.*;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VotersController extends Controller {
	
	private final FormFactory formFactory;
	private final Configuration configuration;
	private final String SECRET_KEY_BASE64_ENCODED;
	private static Pattern pattern;
	private static Matcher matcher;
	private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	private static final String USERNAME_PATTERN = "^[A-Za-z_][A-Za-z0-9_]{5,20}$";
	private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&+=])(?=\\S+$).{8,20}$";

	@Inject
	public VotersController(final FormFactory formFactory, Configuration configuration) {
		this.configuration = configuration;
		this.formFactory = formFactory;
		this.SECRET_KEY_BASE64_ENCODED = configuration.getString("jwt.token.key");
	}

	public Result authenticate() {
		final JsonNode jsonRequestBody = play.libs.Json.toJson(formFactory.form().bindFromRequest().data());
		ObjectNode jsonResponseBody = play.libs.Json.newObject();
		ArrayNode errors = play.libs.Json.newArray();

		if(jsonRequestBody.size() == 0) {
			requestEmptyBody(errors);
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		}

		boolean exitWithError = false;
		//Validate login fields.
		JsonNode grantType = jsonRequestBody.findPath("grant_type");
		if(grantType.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "grant_type");
		} else if(!isGrantTypeValid(grantType.textValue())) {
			exitWithError = true;
			requestValidationError(errors, "grant_type");
		}
		JsonNode username = jsonRequestBody.findPath("username");
		if(username.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "username");
		}
		JsonNode password = jsonRequestBody.findPath("password");
		if(password.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "password");
		}
		if(exitWithError) {
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		}
		if(!isUsernameValid(username.textValue()) || !isPasswordValid(password.textValue())) {
			requestFailedAuthentication(errors);
			jsonResponseBody.set("errors", errors);
			return unauthorized(jsonResponseBody);
		}

		//Check username/password combination.
		Voter loginUser = Voter.findVoterByUsername(username.textValue());
		if(loginUser == null || !BCrypt.checkpw(password.textValue(), loginUser.passwordHash)) {
			requestFailedAuthentication(errors);
			jsonResponseBody.set("errors", errors);
			return unauthorized(jsonResponseBody);
		}

		//Provide a JWT token within the response of a successful login.
		jsonResponseBody.put("accessToken", Token.createAccessToken(loginUser));
		jsonResponseBody.put("tokenType", "bearer");
		return ok(jsonResponseBody);
	}

	@With(SecuredAction.class)
	public Result logout() {
		return forbidden();
	}

	public Result createUser() {
		final JsonNode jsonRequestBody = request().body().asJson();
		ObjectNode jsonResponseBody = play.libs.Json.newObject();
		ArrayNode errors = play.libs.Json.newArray();
		Logger.debug("request: "+ jsonRequestBody.toString());

		if(jsonRequestBody.size() == 0) {
			requestEmptyBody(errors);
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		}
		boolean exitWithError = false;
		//Validate all fields and respond with multiple errors.
		JsonNode email = jsonRequestBody.findPath("email");
		if(email.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "email");
		} else if(!isEmailValid(email.textValue())) {
			exitWithError = true;
			requestValidationError(errors, "email");
		}
		JsonNode username = jsonRequestBody.findPath("username");
		if(username.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "username");
		} else if(!isUsernameValid(username.textValue())) {
			exitWithError = true;
			requestValidationError(errors, "username");
		}
		JsonNode password = jsonRequestBody.findPath("password");
		if(password.isMissingNode()) {
			exitWithError = true;
			requestMissingField(errors, "password");
		} else if(!isPasswordValid(password.textValue())) {
			exitWithError = true;
			requestValidationError(errors, "password");
		}
		if(exitWithError) {
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		}

		//Respond with individual errors in case email/username is already in use.
		if(!isEmailAvailable(email.textValue())) {
			requestIdentifierInUse(errors, "email");
			jsonResponseBody.set("errors", errors);
			return status(409, jsonResponseBody);
		}
		if(!isUsernameAvailable(username.textValue())) {
			requestIdentifierInUse(errors, "username");
			jsonResponseBody.set("errors", errors);
			return status(409, jsonResponseBody);
		}

		//Create user and respond with data.
		Voter registree = new Voter("User", email.textValue(), username.textValue(),
				BCrypt.hashpw(password.textValue(), BCrypt.gensalt()), null);
		registree.save();

		jsonResponseBody.set("data", userDataObject(registree));
		return created(jsonResponseBody);
	}

	@With(SecuredAction.class)
	public Result fetchUserSelf() {
		Voter user = ((Voter) ctx().args.get("user"));
		ObjectNode jsonResponseBody = play.libs.Json.newObject();

		jsonResponseBody.set("data", userDataObject(user));
		return ok(jsonResponseBody);
	}

	@With(SecuredAction.class)
	public Result updateUserSelf() {
		Voter user = ((Voter) ctx().args.get("user"));
		final JsonNode jsonRequestBody = request().body().asJson();
		ObjectNode jsonResponseBody = play.libs.Json.newObject();
		ArrayNode errors = play.libs.Json.newArray();

		if(jsonRequestBody.size() == 0) {
			requestEmptyBody(errors);
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		}
		//Confirm authentication.
		JsonNode password = jsonRequestBody.findPath("password");
		if(password.isMissingNode()) {
			requestMissingField(errors, "password");
			jsonResponseBody.set("errors", errors);
			return badRequest(jsonResponseBody);
		} else if(!BCrypt.checkpw(password.textValue(), user.passwordHash) ) {
			requestFailedAuthentication(errors);
			jsonResponseBody.set("errors", errors);
			return unauthorized(jsonResponseBody);
		}
		boolean updateEmail = false;
		boolean updatePassword = false;
		boolean exitWithError = false;
		//Validate all fields and respond with multiple errors.
		JsonNode newEmail = jsonRequestBody.findPath("newEmail");
		if(!newEmail.isMissingNode()) {
			updateEmail = true;
			if(!isEmailValid(newEmail.textValue())) {
				requestValidationError(errors ,newEmail.textValue());
				exitWithError = true;
			}
		}
		JsonNode newPassword = jsonRequestBody.findPath("newPassword");
		if(!newPassword.isMissingNode()) {
			updatePassword = true;
			if(!isPasswordValid(newPassword.textValue())) {
				requestValidationError(errors, newPassword.textValue());
				exitWithError = true;
			}
		}
		if(exitWithError) {
			jsonResponseBody.set("errors", errors);
			return  badRequest(jsonResponseBody);
		}

		//Update user's fields.
		if(updateEmail) {
			if(isEmailAvailable(newEmail.textValue())) {
				user.email = newEmail.textValue();
				user.timeUpdated = Instant.now();
				user.update();
			}
			else {
				requestIdentifierInUse(errors, "newEmail");
				jsonResponseBody.set("errors", errors);
				return status(409, jsonResponseBody);
			}
		}
		if(updatePassword) {
			user.passwordHash = BCrypt.hashpw(newPassword.textValue(), BCrypt.gensalt());
			user.timeUpdated = Instant.now();
			user.update();
		}

		jsonResponseBody.set("data", userDataObject(user));
		return ok(jsonResponseBody);
	}

	@With(SecuredAction.class)
	public Result fetchUser(String id) {
		//This endpoint is not used.
		return forbidden();
	}

	@With(SecuredAction.class)
	public Result updateUser(String id) {
		//This endpoint is not used.
		return forbidden();
	}

	private static boolean isEmailValid(String email) {
		if(email == null) return false;
		pattern = Pattern.compile(EMAIL_PATTERN);
		matcher = pattern.matcher(email);
		return matcher.matches();
	}
	private static boolean isUsernameValid(String username) {
		if(username == null) return false;
		pattern = Pattern.compile(USERNAME_PATTERN);
		matcher = pattern.matcher(username);
		return matcher.matches();
	}
	private static boolean isPasswordValid(String password) {
		if(password == null) return false;
		pattern = Pattern.compile(PASSWORD_PATTERN);
		matcher = pattern.matcher(password);
		return matcher.matches();
	}
	private static boolean isGrantTypeValid(String grantType) {
		return grantType.equals("password");
	}
	private static boolean isEmailAvailable(String email) {
		//Use this method after validation or perform a null check on email.
		return (Voter.findVoterByEmail(email) == null);
	}
	private static boolean isUsernameAvailable(String username) {
		//Use this method after validation or perform a null check on email.
		return (Voter.findVoterByUsername(username) == null);
	}

	public static ObjectNode userDataObject(Voter user) {
		//Resource type and id.
		ObjectNode data = Json.newObject();
		data.put("type", "users");
		data.put("id", user.code);
		//Resource attributes.
		ObjectNode attributes = Json.newObject();
		attributes.put("email", user.email);
		attributes.put("username", user.username);
		attributes.put("timeCreated", user.timeCreated.toString());
		attributes.put("timeUpdated", user.timeUpdated.toString());
		data.set("attributes", attributes);
		//Resource links.
		ObjectNode links = play.libs.Json.newObject();
		links.put("self", routes.VotersController.fetchUser(user.code).absoluteURL(request()));
		data.set("links", links);
		return data;
	}

	private static void requestEmptyBody(ArrayNode errorsArray) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", 400);
		error.put("code", 1);
		error.put("title", "Request body is empty.");
		error.put("detail", "Json body is empty.");
		error.put("moreInfo", "https://localhost:9000/documentation#users");
		errorsArray.add(error);
	}
	private static void requestMissingField(ArrayNode errorsArray, String field) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", 400);
		error.put("code", 2);
		error.put("title", "Missing field: " + field + ".");
		error.put("detail", "The json body is missing the " + field + " field.");
		error.put("moreInfo", "https://localhost:9000/documentation#users");
		errorsArray.add(error);
	}
	private static void requestValidationError(ArrayNode errorsArray, String field) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", 422);
		error.put("code", 3);
		error.put("title", "Invalid " + field + ".");
		error.put("detail", "The json value for " + field + " is invalid.");
		error.put("moreInfo", "https://localhost:9000/documentation#users");
		errorsArray.add(error);
	}
	private static void requestIdentifierInUse(ArrayNode errorsArray, String field) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", 409);
		error.put("code", 4);
		error.put("title", "This " + field + " is already in use.");
		error.put("detail", "The " + field + " is already in use. Please choose another one.");
		error.put("moreInfo", "https://localhost:9000/documentation#users");
		errorsArray.add(error);
	}
	private static void requestFailedAuthentication(ArrayNode errorsArray) {
		ObjectNode error = play.libs.Json.newObject();
		error.put("status", 401);
		error.put("code", 5);
		error.put("title", "Wrong username/password combination.");
		error.put("detail", "Wrong username/password combination.");
		error.put("moreInfo", "https://localhost:9000/documentation#users");
		errorsArray.add(error);
	}
}