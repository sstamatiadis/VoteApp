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

public class VotesController extends Controller {

    private final Configuration configuration;
    private final String SECRET_KEY_BASE64_ENCODED;

    @Inject
    public VotesController(Configuration configuration) {
        this.configuration = configuration;
        this.SECRET_KEY_BASE64_ENCODED = configuration.getString("jwt.token.key");
    }

    @With(SecuredAction.class)
    public Result createVote() {
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
        JsonNode pollId = jsonRequestBody.findPath("poll").findPath("data").get("id");
        if(pollId.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "poll id");
        }
        JsonNode optionIds = jsonRequestBody.findPath("options");
        if(optionIds.isMissingNode() || !optionIds.isArray() || optionIds.size() < 1) {
            exitWithError = true;
            requestMissingField(errors, "options");
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
        //Check if user has been invited.
        Participation participation = Participation.findParticipationByIds(user.id, poll.id);
        if(poll.visibility.equals("Private")) {
            if(participation == null) {
                requestResourceForbidden(errors, "poll");
                jsonResponseBody.set("errors", errors);
                return forbidden(jsonResponseBody);
            }
        }
        //Check if already voted.
        Vote vote = Vote.findVoteByIds(user.id, poll.id);
        if(vote != null) {
            requestResourceConflict(errors, "vote");
            jsonResponseBody.set("errors", errors);
            return status(409, jsonResponseBody);
        }
        //Check if poll has expired.
        if(Instant.now().isAfter(poll.expiration)) {
            requestResourceExpired(errors, "poll");
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }
        if(poll.visibility.equals("Public")) {
            if(participation == null) {
                //Create a participation.
                participation = new Participation(user, poll);
                participation.save();
            }
        }
        //Validate options.
        if(poll.mode.equals("Single")) {
            if(optionIds.size() != 1) {
                requestInvalidOptionCount(errors);
                jsonResponseBody.set("errors", errors);
                return badRequest(jsonResponseBody);
            }
        } else {
            if(optionIds.size() < 1 || optionIds.size() > poll.options.size()) {
                requestInvalidOptionCount(errors);
                jsonResponseBody.set("errors", errors);
                return badRequest(jsonResponseBody);
            }
        }
        List<Option> options = new ArrayList<>();
        for(JsonNode optionId : optionIds) {
            Option option = Option.findOptionByCode(optionId.get("id").textValue());
            if(option == null || !option.poll.id.equals(poll.id)) {
                requestResourceNotFound(errors, "option");
                jsonResponseBody.set("errors", errors);
                return notFound(jsonResponseBody);
            }
            options.add(option);
        }

        //Submit vote.
        vote = new Vote(user, poll);
        vote.save();

        for(Option option : options) {
            option.votes++;
            option.timeUpdated = Instant.now();
            option.update();
        }

        jsonResponseBody.set("data", voteDataObject(vote));
        return created(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchVote(String id) {
        return forbidden();
    }

    private static ObjectNode voteDataObject(Vote vote) {
        ObjectNode resource = Json.newObject();
        //Resource type and id.
        resource.put("type", "votes");
        resource.put("id", vote.code);
        //Resource attributes.
        ObjectNode attributes = Json.newObject();
        attributes.put("timeUpdated", vote.timeUpdated.toString());
        attributes.put("timeCreated", vote.timeCreated.toString());
        resource.set("attributes", attributes);
        //Resource relationships.
        ObjectNode relationships = Json.newObject();
        //Poll relationship.
        ObjectNode poll = Json.newObject();
        //Poll links.
        ObjectNode pollLinks = Json.newObject();
        pollLinks.put("self", routes.PollsController.fetchPoll(vote.poll.code).absoluteURL(request(), true));
        poll.set("links", pollLinks);
        //Poll data.
        ObjectNode pollData = Json.newObject();
        pollData.put("type", "polls");
        pollData.put("id", vote.poll.code);
        poll.set("data", pollData);
        relationships.set("poll", poll);
        //User relationships.
        ObjectNode user = Json.newObject();
        //User links.
        ObjectNode userLinks = Json.newObject();
        userLinks.put("self", routes.VotersController.fetchUser(vote.voter.code).absoluteURL(request(), true));
        user.set("links", userLinks);
        //User data.
        ObjectNode userData = Json.newObject();
        userData.put("type", "users");
        userData.put("id", vote.voter.code);
        user.set("data", userData);
        relationships.set("user", user);
        resource.set("relationships", relationships);
        //Resource links.
        ObjectNode links = Json.newObject();
        links.put("self", routes.VotesController.fetchVote(vote.code).absoluteURL(request(), true));
        resource.set("links", links);
        return resource;
    }

    private static void requestEmptyBody(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 1);
        error.put("title", "Request body is empty.");
        error.put("detail", "Json body is empty.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestMissingField(ArrayNode errorsArray, String field) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 2);
        error.put("title", "Missing field: " + field + ".");
        error.put("detail", "The json body is missing the " + field + " field.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestResourceForbidden(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 403);
        error.put("code", 7);
        error.put("title", "Resource " + resource + " is forbidden.");
        error.put("detail", "Resource " + resource + " is forbidden. A participation is required.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestResourceNotFound(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 404);
        error.put("code", 8);
        error.put("title", "Resource " + resource + " not found.");
        error.put("detail", "There was no " + resource + " found with the supplied id.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestResourceConflict(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 409);
        error.put("code", 10);
        error.put("title", "Resource " + resource + " conflict.");
        error.put("detail", "There is already a " + resource + " resource created.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestResourceExpired(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 11);
        error.put("title", "Resource " + resource + " has expired.");
        error.put("detail", "Vote was rejected due to " + resource + " expiration.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
    private static void requestInvalidOptionCount(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 422);
        error.put("code", 12);
        error.put("title", "Invalid option count.");
        error.put("detail", "Invalid number of options.");
        error.put("moreInfo", "https://localhost:9000/documentation#votes");
        errorsArray.add(error);
    }
}
