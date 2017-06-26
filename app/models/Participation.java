package models;

import com.avaje.ebean.Model;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@Entity
public class Participation extends Model {

    @EmbeddedId @NotNull
    ParticipationId id;
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

    public Participation(Voter voter, Poll poll) {
        this.id = new ParticipationId();
        id.voter_id = voter.id;
        id.poll_id = poll.id;
        this.code = Utils.generateUniqueParticipationCode();
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

    public static Finder<Long, Participation> find = new Model.Finder<>(Participation.class);

    public static Participation findParticipationByIds(Long userId, Long pollId) {
        return find
                .where()
                .conjunction()
                .eq("voter.id", userId)
                .eq("poll.id", pollId)
                .endJunction()
                .findUnique();
    }
}
