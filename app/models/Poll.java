package models;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.avaje.ebean.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Poll extends Model {

    @Id @GeneratedValue @NotNull
    public Long id;
    @Column(nullable = false)
    public String code;
    @Column(nullable = false)
    public String status;
    @Column @ManyToOne(optional = false)
    public Voter creator;
    @Column(nullable = false)
    public String visibility;
    @Column(nullable = false)
    public String mode;
    @Column(nullable = false) @Size(min = 10, max = 200)
    public String question;
    @Column(nullable = false)
    public Instant expiration;
    @Column(nullable = false)
    public Instant timeUpdated;
    @Column(nullable = false)
    public Instant timeCreated;

    @OneToMany(mappedBy = "poll", cascade = {CascadeType.PERSIST})
    public List<Option> options;
    @OneToMany(mappedBy = "poll")
    public List<Vote> votes;
    @OneToMany(mappedBy = "poll")
    public List<Participation> participations;

    public Poll(Voter creator, String visibility, String mode, String question, Instant expiration) {
        this.code = Utils.generateUniquePollCode();
        this.status = "Active";
        this.creator = creator;
        this.visibility = visibility;
        this.mode = mode;
        this.question = question;
        this.expiration = expiration;
        Instant currentInstant = Instant.now();
        this.timeUpdated = currentInstant;
        this.timeCreated = currentInstant;
        this.options = new ArrayList<>();
    }

    public static Finder<Long, Poll> find = new Finder<Long, Poll>(Poll.class);

    public static Poll findPollByCode(String id) {
        return find
                .fetch("creator")
                .where()
                .eq("code", id)
                .findUnique();
    }

    public static PagedList<Poll> findPublicPollsPagedList(int page, int size) {
        return find
                .fetch("creator")
                .where()
                .eq("visibility", "Public")
                .order().desc("timeCreated")
                .setMaxRows(size)
                .findPagedList(page, size);
    }

    public static PagedList<Poll> findPrivatePollsPagedList(int page, int size, List<Long> pollIds) {
        return find
                .fetch("creator")
                .where()
                .idIn(pollIds)
                .eq("visibility", "Private")
                .order().desc("timeCreated")
                .setMaxRows(size)
                .findPagedList(page, size);
    }

    public static PagedList<Poll> findCreatedPollsPagedList(int page, int size, Long creatorId) {
        return find
                .fetch("creator")
                .where()
                .eq("creator.id", creatorId)
                .order().desc("timeCreated")
                .setMaxRows(size)
                .findPagedList(page, size);
    }
}