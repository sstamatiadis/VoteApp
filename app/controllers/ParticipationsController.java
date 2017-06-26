package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import play.Configuration;
import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;

import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ParticipationsController extends Controller {

    private final Configuration configuration;
    private final String SECRET_KEY_BASE64_ENCODED;

    @Inject
    public ParticipationsController(Configuration configuration) {
        this.configuration = configuration;
        this.SECRET_KEY_BASE64_ENCODED = configuration.getString("jwt.token.key");
    }

    @With(SecuredAction.class)
    public Result createParticipation() {
        Voter user = (Voter) ctx().args.get("user");
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
        JsonNode pollId = jsonRequestBody.findPath("poll").findPath("id");
        if(pollId.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "poll id");
        }
        JsonNode username = jsonRequestBody.findPath("username");
        if(username.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "username");
        }
        if(exitWithError) {
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        //Respond with individual errors.
        Poll poll = Poll.findPollByCode(pollId.textValue());
        if(poll == null) {
            requestResourceNotFound(errors, "poll");
            jsonResponseBody.set("errors", errors);
            return notFound(jsonResponseBody);
        }
        if(!poll.creator.id.equals(user.id)) {
            requestResourceForbidden(errors, "poll");
            jsonResponseBody.set("errors", errors);
            return forbidden(jsonResponseBody);
        }

        Voter voter = Voter.findVoterByUsername(username.textValue());
        if(voter == null) {
            requestResourceNotFound(errors, "user");
            jsonResponseBody.set("errors", errors);
            return notFound(jsonResponseBody);
        }

        Participation participation = Participation.findParticipationByIds(voter.id, poll.id);
        if(participation != null) {
            requestResourceConflict(errors, "participation");
            jsonResponseBody.set("errors", errors);
            return status(409, jsonResponseBody);
        }

        if(Instant.now().isAfter(poll.expiration)) {
            requestResourceExpired(errors, "poll");
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        //Save participation.
        participation = new Participation(voter, poll);
        participation.save();

        jsonResponseBody.set("data", participationDataObject(participation));
        return created(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchParticipation(String id) {
        return forbidden();
    }

    @With(SecuredAction.class)
    public Result deleteParticipation(String participationId) {
        return forbidden();
    }

    private static ObjectNode participationDataObject(Participation participation) {
        ObjectNode resource = Json.newObject();
        //Resource type and id.
        resource.put("type", "participations");
        resource.put("id", participation.code);
        //Resource attributes.
        ObjectNode attributes = Json.newObject();
        attributes.put("timeUpdated", participation.timeUpdated.toString());
        attributes.put("timeCreated", participation.timeCreated.toString());
        resource.set("attributes", attributes);
        //Resource relationships.
        ObjectNode relationships = Json.newObject();
        //Poll relationship.
        ObjectNode poll = Json.newObject();
        //Poll links.
        ObjectNode pollLinks = Json.newObject();
        pollLinks.put("self", routes.PollsController.fetchPoll(participation.poll.code).absoluteURL(request(), true));
        poll.set("links", pollLinks);
        //Poll data.
        ObjectNode pollData = Json.newObject();
        pollData.put("type", "polls");
        pollData.put("id", participation.poll.code);
        poll.set("data", pollData);
        relationships.set("poll", poll);
        //User relationships.
        ObjectNode user = Json.newObject();
        //User links.
        ObjectNode userLinks = Json.newObject();
        userLinks.put("self", routes.VotersController.fetchUser(participation.voter.code).absoluteURL(request(), true));
        user.set("links", userLinks);
        //User data.
        ObjectNode userData = Json.newObject();
        userData.put("type", "users");
        userData.put("id", participation.voter.code);
        user.set("data", userData);
        relationships.set("user", user);
        resource.set("relationships", relationships);
        //Resource links.
        ObjectNode links = Json.newObject();
        links.put("self", routes.ParticipationsController.fetchParticipation(participation.code).absoluteURL(request(), true));
        resource.set("links", links);
        return resource;
    }

    private static void requestEmptyBody(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 1);
        error.put("title", "Request body is empty.");
        error.put("detail", "Json body is empty.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
    private static void requestMissingField(ArrayNode errorsArray, String field) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 2);
        error.put("title", "Missing field: " + field + ".");
        error.put("detail", "The json body is missing the " + field + " field.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
    private static void requestResourceForbidden(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 403);
        error.put("code", 7);
        error.put("title", "Resource " + resource + " is forbidden.");
        error.put("detail", "Resource " + resource + " is forbidden.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
    private static void requestResourceNotFound(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 404);
        error.put("code", 8);
        error.put("title", "Resource " + resource + " not found.");
        error.put("detail", "There was no " + resource + " found with the supplied id.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
    private static void requestResourceConflict(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 409);
        error.put("code", 10);
        error.put("title", "Resource " + resource + " conflict.");
        error.put("detail", "There is already a " + resource + " resource created for one of the users.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
    private static void requestResourceExpired(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 11);
        error.put("title", "Resource " + resource + " has expired.");
        error.put("detail", "Participation was rejected due to " + resource + " expiration.");
        error.put("moreInfo", "https://localhost:9000/documentation#participations");
        errorsArray.add(error);
    }
}
