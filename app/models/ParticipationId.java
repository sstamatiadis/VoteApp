package models;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class ParticipationId implements Serializable {

    public Long voter_id;
    public Long poll_id;

    public int hashCode() {
        return Long.hashCode(voter_id + poll_id);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        ParticipationId participationId = (ParticipationId) obj;
        if(participationId == null) {
            return false;
        }
        if(participationId.voter_id == voter_id && participationId.poll_id == poll_id) {
            return true;
        }
        return false;
    }

}
