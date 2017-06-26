package models;

import com.avaje.ebean.Model;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@Entity
public class Vote extends Model {

    @EmbeddedId @NotNull
    VoteId id;
    @Column(nullable = false)
    public String code;
    @ManyToOne(optional = false) @JoinColumn(name = "voter_id", insertable = false, updatable = false)
    public Voter voter;
    @ManyToOne(optional = false) @JoinColumn(name = "poll_id", insertable = false, updatable = false)
    public Poll poll;
    @Column(nullable = false)
    public Instant timeUpdated;
    @Column(nullable = false)
    public Instant timeCreated;

    public Vote(Voter voter, Poll poll) {
        this.id = new VoteId();
        id.voter_id = voter.id;
        id.poll_id = poll.id;
        this.code = Utils.generateUniqueVoteCode();
        this.voter = voter;
        this.poll = poll;
        Instant currentInstant = Instant.now();
        this.timeUpdated = currentInstant;
        this.timeCreated = currentInstant;
    }

    public Voter getVoter() {
        return voter;
    }

    public void setVoter(Voter voter) {
        this.voter = voter;
        this.id.voter_id = voter.id;
    }

    public Poll getPoll() {
        return poll;
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
        this.id.poll_id = poll.id;
    }

    public static Finder<Long, Vote> find = new Finder<>(Vote.class);

    public static Vote findVoteByIds(Long userId, Long pollId) {
        return find
                .where()
                .conjunction()
                .eq("voter.id", userId)
                .eq("poll.id", pollId)
                .endJunction()
                .findUnique();
    }
}

