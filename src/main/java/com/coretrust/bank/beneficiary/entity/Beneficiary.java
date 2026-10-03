package com.coretrust.bank.beneficiary.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(name = "uk_beneficiaries_user_account",
                columnNames = {"user_id", "account_number"}),
        indexes = @Index(name = "idx_beneficiaries_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_beneficiaries_user"))
    private User user;

    @Column(name = "beneficiary_name", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)   // change only via deactivate() / reactivate()
    private BeneficiaryStatus status = BeneficiaryStatus.ACTIVE;

    public void deactivate() {
        if (status != BeneficiaryStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This beneficiary is already inactive.");
        }
        status = BeneficiaryStatus.INACTIVE;
    }

    /** Re-adding a previously removed beneficiary reuses its record (details may have changed). */
    public void reactivate(String beneficiaryName, String bankName, String ifscCode) {
        if (status != BeneficiaryStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This beneficiary is already active.");
        }
        this.beneficiaryName = beneficiaryName;
        this.bankName = bankName;
        this.ifscCode = ifscCode;
        this.status = BeneficiaryStatus.ACTIVE;
    }
}
