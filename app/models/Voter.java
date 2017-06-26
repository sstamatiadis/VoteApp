package models;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.avaje.ebean.*;
import java.time.Instant;
import java.util.List;

@Entity
public class Voter extends Model {

	@Id @GeneratedValue @NotNull
	public Long id;
	@Column(nullable = false)
	public String status;
	@Column(nullable = false)
	public String code;
	@Column(nullable = false)
	public String role;
	@Column(nullable = false)
	public String email;
	@Column(nullable = false) @Size(min = 5, max = 20)
	public String username;
	@Column
	public String passwordHash;
	@Column
	public String accessToken;
	@Column(nullable = false)
	public Instant timeUpdated;
	@Column(nullable = false)
	public Instant timeCreated;

	@OneToMany(mappedBy = "creator")
	public List<Poll> createdPolls;
	@OneToMany(mappedBy = "voter")
	public List<Vote> votedPolls;
	@OneToMany(mappedBy = "voter")
	public List<Participation> participatedPolls;

	public Voter(String role, String email, String username, String passwordHash, String accessToken) {
		this.status = "Registered";
		this.role = role;
		this.code = Utils.generateUniqueVoterCode();
		this.email = email;
		this.username = username;
		this.passwordHash = passwordHash;
		this.accessToken = accessToken;
		Instant currentInstant = Instant.now();
		this.timeUpdated = currentInstant;
		this.timeCreated = currentInstant;
	}
	
	public static Finder<Long, Voter> find = new Finder<Long, Voter>(Voter.class);

	public static Voter findVoterByCode(String code) {
		return find
				.where()
				.eq("code", code)
				.findUnique();
	}

	public static Voter findVoterByUsername(String username) {
		return find
				.where()
				.eq("username", username)
				.findUnique();
	}

	public static Voter findVoterByEmail(String email) {
		return find
				.where()
				.eq("email", email)
				.findUnique();
	}
}