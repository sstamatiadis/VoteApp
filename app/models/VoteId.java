package models;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class VoteId implements Serializable {

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
        VoteId voteId = (VoteId)obj;
        if(voteId == null) {
            return false;
        }
        if(voteId.voter_id == voter_id && voteId.poll_id == poll_id) {
            return true;
        }
        return false;
    }
}
