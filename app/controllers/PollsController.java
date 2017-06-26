package controllers;

import com.avaje.ebean.PagedList;
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
import scala.util.parsing.json.JSONArray;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PollsController extends Controller {

    private static final Set<String> POLL_VISIBILITY_SET = new HashSet<>(Arrays.asList("Public", "Private"));
    private static final Set<String> POLL_MODE_SET = new HashSet<>(Arrays.asList("Single", "Multiple"));
    private static final int POLL_MIN_QUESTION_CHARS = 8;
    private static final int POLL_MAX_QUESTION_CHARS = 250;
    private static final int POLL_MIN_OPTIONS = 2;
    private static final int POLL_MIN_OPTION_CHARS = 1;
    private static final int POLL_MAX_OPTION_CHARS = 100;
    private static final int POLL_MIN_EXPIRATION_DAYS = 1;
    private static final int POLL_MAX_EXPIRATION_DAYS = 30;
    private static final int POLL_DEFAULT_PAGE_SIZE = 10;
    private static final int POLL_MAX_PAGE_SIZE = 100;
    private final Configuration configuration;
    private final String SECRET_KEY_BASE64_ENCODED;

    @Inject
    public PollsController(Configuration configuration) {
        this.configuration = configuration;
        this.SECRET_KEY_BASE64_ENCODED = configuration.getString("jwt.token.key");
    }

    @With(SecuredAction.class)
    public Result createPoll() {
        Voter user = (Voter) ctx().args.get("user");
        final JsonNode jsonRequestBody = request().body().asJson();
        ObjectNode jsonResponseBody = play.libs.Json.newObject();
        ArrayNode errors = play.libs.Json.newArray();
        Logger.debug("request: "+ jsonRequestBody.toString());

        if(jsonRequestBody == null) {
            requestEmptyBody(errors);
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        boolean exitWithError = false;
        //Validate all fields and respond with multiple errors.
        JsonNode visibility = jsonRequestBody.findPath("visibility");
        if(visibility.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "visibility");
        } else if(!POLL_VISIBILITY_SET.contains(visibility.textValue())) {
            exitWithError = true;
            requestValidationError(errors, "visibility");
        }
        JsonNode mode = jsonRequestBody.findPath("mode");
        if(mode.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "mode");
        } else if(!POLL_MODE_SET.contains(mode.textValue())) {
            exitWithError = true;
            requestValidationError(errors, "mode");
        }
        JsonNode question = jsonRequestBody.findPath("question");
        if(question.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "question");
        } else if(!isPollQuestionLengthValid(question.textValue())) {
            exitWithError = true;
            requestValidationError(errors, "question");
        }
        JsonNode expiration = jsonRequestBody.findPath("expiration");
        if(expiration.isMissingNode()) {
            exitWithError = true;
            requestMissingField(errors, "expiration");

        } else if(!isPollExpirationValid(Integer.parseInt(expiration.textValue()))) {
            exitWithError = true;
            requestValidationError(errors, "expiration");
        }
        List<Option> optionsList = new ArrayList<>();
        JsonNode options = jsonRequestBody.findPath("options");
        if(options.isMissingNode() || !options.isArray()) {
            exitWithError = true;
            requestMissingField(errors, "options");
        } else if (options.size() < POLL_MIN_OPTIONS) {
            exitWithError = true;
            requestNotEnoughOptions(errors);
        } else {
            for(JsonNode option : options) {
                if (!isPollOptionLengthValid(option.get("option").textValue())) {
                    exitWithError = true;
                    requestValidationError(errors, "option (" + option.get("option").textValue() + ")");
                }
                optionsList.add(new Option(option.get("option").textValue()));
            }
        }
        if(exitWithError) {
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        //Save Poll along with options.
        Poll newPoll = new Poll(
                user,
                visibility.textValue(),
                mode.textValue(),
                question.textValue(),
                Instant.now().plus(Integer.parseInt(expiration.textValue()), ChronoUnit.DAYS));

        newPoll.options = optionsList;
        newPoll.save();

        //Creator participates by default.
        Participation creatorParticipation = new Participation(user, newPoll);
        creatorParticipation.save();

        jsonResponseBody.set("data", pollDataObject(newPoll));
        return created(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchPoll(String id) {
        Voter user = (Voter) ctx().args.get("user");
        ObjectNode jsonResponseBody = play.libs.Json.newObject();
        ArrayNode errors = play.libs.Json.newArray();

        //Check if poll exists.
        final Poll poll = Poll.findPollByCode(id);
        if(poll == null) {
            requestResourceNotFound(errors, "poll");
            jsonResponseBody.set("errors", errors);
            return notFound(jsonResponseBody);
        }

        if(poll.visibility.equals("Private")) {
            final Participation participation = Participation.findParticipationByIds(user.id, poll.id);
            if(participation == null) {
                requestResourceForbidden(errors, "poll");
                jsonResponseBody.set("errors", errors);
                return forbidden(jsonResponseBody);
            }
        }

        pollSingle(jsonResponseBody, poll);
        return ok(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchPollsPublic(int page, int size) {
        ObjectNode jsonResponseBody = Json.newObject();
        ArrayNode errors = play.libs.Json.newArray();

        if(size == 0) size = 10;
        if(size > POLL_MAX_PAGE_SIZE) {
            requestPageSizeTooBig(errors);
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        PagedList<Poll> pagedList = Poll.findPublicPollsPagedList(page, size);
        //Optional: We could initiate counting in a background thread or use a Future.
        pagedList.getTotalRowCount();

        pollsPaginated(jsonResponseBody, pagedList, "public");
        return ok(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchPollsPrivate(int page, int size) {
        Voter user = (Voter) ctx().args.get("user");
        ObjectNode jsonResponseBody = Json.newObject();
        ArrayNode errors = play.libs.Json.newArray();

        if(size == 0) size = 10;
        if(size > POLL_MAX_PAGE_SIZE) {
            requestPageSizeTooBig(errors);
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        List<Long> participatedPollIds = new ArrayList<>();
        for (Participation participation : user.participatedPolls) {
            participatedPollIds.add(participation.poll.id);
        }

        PagedList<Poll> pagedList = Poll.findPrivatePollsPagedList(page, size, participatedPollIds);
        //Optional: We could initiate counting in a background thread or use a Future.
        pagedList.getTotalRowCount();

        pollsPaginated(jsonResponseBody, pagedList, "private");
        return ok(jsonResponseBody);
    }

    @With(SecuredAction.class)
    public Result fetchPollsCreated(int page, int size) {
        Voter user = (Voter) ctx().args.get("user");
        ObjectNode jsonResponseBody = Json.newObject();
        ArrayNode errors = play.libs.Json.newArray();

        if(size == 0) size = 10;
        if(size > POLL_MAX_PAGE_SIZE) {
            requestPageSizeTooBig(errors);
            jsonResponseBody.set("errors", errors);
            return badRequest(jsonResponseBody);
        }

        PagedList<Poll> pagedList = Poll.findCreatedPollsPagedList(page, size, user.id);
        //Optional: We could initiate counting in a background thread or use a Future.
        pagedList.getTotalRowCount();

        pollsPaginated(jsonResponseBody, pagedList, "created");
        return ok(jsonResponseBody);
    }

    //Todo: Do i need to to fetchPollsParticipated?

    private static boolean isPollExpirationValid(int days) {
        return days >= POLL_MIN_EXPIRATION_DAYS && days <= POLL_MAX_EXPIRATION_DAYS;
    }
    private static boolean isPollQuestionLengthValid(String question) {
        return question.length() >= POLL_MIN_QUESTION_CHARS && question.length() <= POLL_MAX_QUESTION_CHARS;
    }
    private static boolean isPollOptionLengthValid(String option) {
        return option.length() >= POLL_MIN_OPTION_CHARS && option.length() <= POLL_MAX_OPTION_CHARS;
    }

    public static ObjectNode pollDataObject(Poll poll) {
        ObjectNode resource = Json.newObject();
        //Resource type and id.
        resource.put("type", "polls");
        resource.put("id", poll.code);
        //Resource attributes.
        ObjectNode attributes = Json.newObject();
        attributes.put("visibility", poll.visibility);
        attributes.put("mode", poll.mode);
        attributes.put("question", poll.question);
        ArrayNode options = pollOptionsArray(poll);
        attributes.set("options", options);
        attributes.put("expiration", poll.expiration.toString());
        attributes.put("timeUpdated", poll.timeUpdated.toString());
        attributes.put("timeCreated", poll.timeCreated.toString());
        resource.set("attributes", attributes);
        //Resource relationships.
        ObjectNode relationships = Json.newObject();
        //Creator relationship.
        ObjectNode creator = Json.newObject();
        //Creator links.
        ObjectNode creatorLinks = Json.newObject();
        creatorLinks.put("self", routes.VotersController.fetchUser(poll.creator.code).absoluteURL(request(), true));
        creator.set("links", creatorLinks);
        //Creator data.
        ObjectNode creatorData = Json.newObject();
        creatorData.put("type", "users");
        creatorData.put("id", poll.creator.code);
        creator.set("data", creatorData);
        relationships.set("creator" ,creator);
        resource.set("relationships", relationships);
        //Resource links.
        ObjectNode links = Json.newObject();
        links.put("self", routes.PollsController.fetchPoll(poll.code).absoluteURL(request(), true));
        resource.set("links", links);
        return resource;
    }
    private static ArrayNode pollOptionsArray(Poll poll) {
        ArrayNode options = Json.newArray();
        for (Option pollOption : poll.options) {
            ObjectNode option = Json.newObject();
            option.put("id", pollOption.code);
            option.put("option", pollOption.option);
            option.put("votes", pollOption.votes);
            options.add(option);
        }
        return options;
    }
    private static ObjectNode pollPaginationLinks(PagedList<Poll> pagedList, String path) {
        ObjectNode links = Json.newObject();
        switch(path) {
            case "public":
                links.put("first", routes.PollsController.fetchPollsPublic(0, pagedList.getPageSize()).absoluteURL(request(), true));
                links.put("last", routes.PollsController.fetchPollsPublic((int) Math.ceil((double)pagedList.getTotalRowCount()/pagedList.getPageSize())-1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasNext())
                    links.put("next", routes.PollsController.fetchPollsPublic(pagedList.getPageIndex()+1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasPrev())
                    links.put("prev", routes.PollsController.fetchPollsPublic(pagedList.getPageIndex()-1, pagedList.getPageSize()).absoluteURL(request(), true));
                break;

            case "private":
                links.put("first", routes.PollsController.fetchPollsPrivate(0, pagedList.getPageSize()).absoluteURL(request(), true));
                links.put("last", routes.PollsController.fetchPollsPrivate((int) Math.ceil((double)pagedList.getTotalRowCount()/pagedList.getPageSize())-1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasNext())
                    links.put("next", routes.PollsController.fetchPollsPrivate(pagedList.getPageIndex()+1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasPrev())
                    links.put("prev", routes.PollsController.fetchPollsPrivate(pagedList.getPageIndex()-1, pagedList.getPageSize()).absoluteURL(request(), true));
                break;

            case "created":
                links.put("first", routes.PollsController.fetchPollsCreated(0, pagedList.getPageSize()).absoluteURL(request(), true));
                links.put("last", routes.PollsController.fetchPollsCreated((int) Math.ceil((double)pagedList.getTotalRowCount()/pagedList.getPageSize())-1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasNext())
                    links.put("next", routes.PollsController.fetchPollsCreated(pagedList.getPageIndex()+1, pagedList.getPageSize()).absoluteURL(request(), true));
                if(pagedList.hasPrev())
                    links.put("prev", routes.PollsController.fetchPollsCreated(pagedList.getPageIndex()-1, pagedList.getPageSize()).absoluteURL(request(), true));
                break;

            default:
                return null;
        }



        return links;
    }
    private static void pollSingle(ObjectNode jsonResponseBody, Poll poll) {
        ArrayNode included = Json.newArray();
        included.add(VotersController.userDataObject(poll.creator));
        jsonResponseBody.set("data", pollDataObject(poll));
        jsonResponseBody.set("included", included);
    }
    private static void pollsPaginated(ObjectNode jsonResponseBody, PagedList<Poll> pagedList, String path) {
        ArrayNode data = Json.newArray();
        ArrayNode included = Json.newArray();
        Set<String> creatorUniqueIds = new HashSet<>();
        // Add all polls from current paged list to the data array but, add
        // only the unique poll creators to the included array per json-api spec.
        for(Poll poll : pagedList.getList()) {
            if(!creatorUniqueIds.contains(poll.creator.code)) {
                creatorUniqueIds.add(poll.creator.code);
                included.add(VotersController.userDataObject(poll.creator));
            }
            data.add(pollDataObject(poll));
        }
        jsonResponseBody.set("links", pollPaginationLinks(pagedList, path));
        jsonResponseBody.set("data", data);
        jsonResponseBody.set("included", included);
    }

    private static void requestEmptyBody(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 1);
        error.put("title", "Request body is empty.");
        error.put("detail", "Json body is empty.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestMissingField(ArrayNode errorsArray, String field) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 400);
        error.put("code", 2);
        error.put("title", "Missing field: " + field + ".");
        error.put("detail", "The json body is missing the " + field + " field.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestValidationError(ArrayNode errorsArray, String field) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 422);
        error.put("code", 3);
        error.put("title", "Invalid " + field + ".");
        error.put("detail", "The json value for " + field + " is invalid.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestNotEnoughOptions(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 422);
        error.put("code", 6);
        error.put("title", "Not enough options.");
        error.put("detail", "A poll needs at least two option in order to be valid.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestResourceForbidden(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 403);
        error.put("code", 7);
        error.put("title", "Resource " + resource + " is forbidden.");
        error.put("detail", "Resource " + resource + " is forbidden. A participation is required.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestResourceNotFound(ArrayNode errorsArray, String resource) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 404);
        error.put("code", 8);
        error.put("title", "Resource " + resource + " not found.");
        error.put("detail", "There was no " + resource + " found with the supplied id.");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
    private static void requestPageSizeTooBig(ArrayNode errorsArray) {
        ObjectNode error = play.libs.Json.newObject();
        error.put("status", 422);
        error.put("code", 9);
        error.put("title", "The page size parameter is too big.");
        error.put("detail", "The page size is too big. The default page size is " + POLL_DEFAULT_PAGE_SIZE + " and the max is " + POLL_MAX_PAGE_SIZE + ".");
        error.put("moreInfo", "https://localhost:9000/documentation#polls");
        errorsArray.add(error);
    }
}
