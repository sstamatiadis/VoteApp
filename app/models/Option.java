package models;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.avaje.ebean.*;
import java.time.Instant;

@Entity
public class Option extends Model {

    @Id @GeneratedValue @NotNull
    public Long id;
    @Column(nullable = false)
    public String code;
    @Column @ManyToOne
    public Poll poll;
    @Column(nullable = false) @Size(min = 2, max = 100)
    public String option;
    @Column(nullable = false)
    public int votes;
    @Column(nullable = false)
    public Instant timeUpdated;
    @Column(nullable = false)
    public Instant timeCreated;

    public Option(String option) {
        this.code = Utils.generateUniqueOptionCode();
        this.option = option;
        this.votes = 0;
        Instant currentInstant = Instant.now();
        this.timeUpdated = currentInstant;
        this.timeCreated = currentInstant;
    }

    public static Finder<Long, Option> find = new Finder<Long, Option>(Option.class);

    public static Option findOptionByCode(String id) {
        return find
                .where()
                .eq("code", id)
                .findUnique();
    }
}