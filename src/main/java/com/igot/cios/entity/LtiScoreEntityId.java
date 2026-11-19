package com.igot.cios.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LtiScoreEntityId implements Serializable {
    private String lineItemId;
    private String userId;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LtiScoreEntityId that = (LtiScoreEntityId) obj;
        return lineItemId.equals(that.lineItemId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return 31 * lineItemId.hashCode() + userId.hashCode();
    }
}
