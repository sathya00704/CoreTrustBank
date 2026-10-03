package com.coretrust.bank.beneficiary.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.beneficiary.dto.AssistedBeneficiaryOtpRequest;
import com.coretrust.bank.beneficiary.dto.AssistedBeneficiaryRequest;
import com.coretrust.bank.beneficiary.dto.BeneficiaryLookupResponse;
import com.coretrust.bank.beneficiary.dto.BeneficiaryRequest;
import com.coretrust.bank.beneficiary.dto.BeneficiaryResponse;
import com.coretrust.bank.beneficiary.entity.Beneficiary;
import com.coretrust.bank.beneficiary.entity.BeneficiaryStatus;
import com.coretrust.bank.beneficiary.event.BeneficiaryAddedEvent;
import com.coretrust.bank.beneficiary.event.BeneficiaryRemovedEvent;
import com.coretrust.bank.beneficiary.repository.BeneficiaryRepository;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.repository.UserRepository;
import com.coretrust.bank.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private static final String ADD_BENEFICIARY_OPERATION =
            "ADD_BENEFICIARY|customer=%d|account=%s|ifsc=%s|name=%s";

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.bank.ifsc}")
    private String bankIfsc;

    @Value("${app.bank.name}")
    private String bankName;

    /** Final beneficiary details after server-side resolution. */
    private record Details(String name, String accountNumber, String bankName, String ifsc) {
    }

    // ---------- lookup (autofill) ----------

    @Transactional(readOnly = true)
    public BeneficiaryLookupResponse lookup(SecurityUser principal, String ifscCode, String accountNumber) {
        String ifsc = ifscCode.trim().toUpperCase(Locale.ROOT);
        if (!ifsc.equals(bankIfsc)) {
            rejectFakeCoreTrustIfsc(ifsc);
            return new BeneficiaryLookupResponse(false, null, null);
        }
        Long ownerId = principal.isStaff() ? null : principal.id();
        Account target = findInternalTarget(ownerId, accountNumber.trim());
        return new BeneficiaryLookupResponse(true, bankName, MaskingUtils.maskName(target.getUser().getName()));
    }

    // ---------- customer self-service ----------

    @Transactional
    public BeneficiaryResponse addBeneficiary(SecurityUser principal, BeneficiaryRequest request) {
        User owner = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));

        Beneficiary saved = createOrReactivate(owner, resolve(owner.getId(), request));
        auditService.record(AuditAction.BENEFICIARY_ADDED, AuditEntityType.BENEFICIARY, saved.getId(),
                "Beneficiary %s (%s) added by customer".formatted(saved.getBeneficiaryName(), saved.getAccountNumber()));
        publishAdded(owner, saved, "you");
        return toResponse(saved);
    }

    @Transactional
    public void removeBeneficiary(SecurityUser principal, Long beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(beneficiaryId, principal.id())
                .filter(b -> b.getStatus() == BeneficiaryStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", beneficiaryId));
        beneficiary.deactivate();
        auditService.record(AuditAction.BENEFICIARY_DEACTIVATED, AuditEntityType.BENEFICIARY, beneficiaryId,
                "Beneficiary %s removed by customer".formatted(beneficiary.getAccountNumber()));
        publishRemoved(beneficiary, "you");
    }

    // ---------- staff-assisted ----------

    @Transactional
    public OtpChallengeResponse requestAssistedOtp(SecurityUser staff, AssistedBeneficiaryOtpRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        Details details = resolve(customer.getId(), request.beneficiary());
        ensureNotAlreadyActive(customer.getId(), details.accountNumber());

        return otpService.issue(customer, OtpPurpose.ADD_BENEFICIARY,
                operation(customer.getId(), details),
                "add %s (account %s, %s) as a beneficiary (requested by CoreTrust support on your behalf)".formatted(
                        details.name(), MaskingUtils.maskAccountNumber(details.accountNumber()), details.bankName()),
                staff.id());
    }

    @Transactional
    public BeneficiaryResponse addAssisted(SecurityUser staff, AssistedBeneficiaryRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        Details details = resolve(customer.getId(), request.beneficiary());
        otpService.verify(request.otpId(), customer.getId(), OtpPurpose.ADD_BENEFICIARY,
                operation(customer.getId(), details), request.otpCode());

        Beneficiary saved = createOrReactivate(customer, details);
        auditService.record(AuditAction.BENEFICIARY_ADDED, AuditEntityType.BENEFICIARY, saved.getId(),
                "Beneficiary %s (%s) added by %s %s for customer %s (OTP verified)".formatted(
                        saved.getBeneficiaryName(), saved.getAccountNumber(),
                        staff.role(), staff.email(), customer.getEmail()));
        publishAdded(customer, saved, "CoreTrust Support, at your request");
        return toResponse(saved);
    }

    @Transactional
    public BeneficiaryResponse deactivateAssisted(SecurityUser staff, Long beneficiaryId, String reason) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", beneficiaryId));
        beneficiary.deactivate();
        auditService.record(AuditAction.BENEFICIARY_DEACTIVATED, AuditEntityType.BENEFICIARY, beneficiaryId,
                "Beneficiary %s deactivated by %s %s on customer request. Reason: %s".formatted(
                        beneficiary.getAccountNumber(), staff.role(), staff.email(), reason));
        publishRemoved(beneficiary, "CoreTrust Support, at your request");
        return toResponse(beneficiary);
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public PageResponse<BeneficiaryResponse> list(SecurityUser principal, Long userId,
                                                  BeneficiaryStatus status, Pageable pageable) {
        Page<Beneficiary> page;
        if (principal.isStaff()) {
            if (userId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "userId is required.");
            }
            page = beneficiaryRepository.findByUserIdAndStatus(userId,
                    status == null ? BeneficiaryStatus.ACTIVE : status, pageable);
        } else {
            page = beneficiaryRepository.findByUserIdAndStatus(principal.id(), BeneficiaryStatus.ACTIVE, pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BeneficiaryResponse get(SecurityUser principal, Long beneficiaryId) {
        Beneficiary beneficiary = (principal.isStaff()
                ? beneficiaryRepository.findById(beneficiaryId)
                : beneficiaryRepository.findByIdAndUserId(beneficiaryId, principal.id())
                .filter(b -> b.getStatus() == BeneficiaryStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", beneficiaryId));
        return toResponse(beneficiary);
    }

    // ---------- core rules ----------

    /**
     * CoreTrust account: name and bank come from our own records (client values are ignored).
     * Other bank: the client must provide both; only formats can be checked.
     */
    private Details resolve(Long ownerId, BeneficiaryRequest request) {
        String ifsc = request.ifscCode().trim().toUpperCase(Locale.ROOT);
        String accountNumber = request.accountNumber().trim();

        if (ifsc.equals(bankIfsc)) {
            Account target = findInternalTarget(ownerId, accountNumber);
            return new Details(target.getUser().getName(), accountNumber, bankName, ifsc);
        }

        rejectFakeCoreTrustIfsc(ifsc);
        String name = trimToNull(request.beneficiaryName());
        String bank = trimToNull(request.bankName());
        if (name == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "beneficiaryName is required for accounts at other banks.");
        }
        if (bank == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "bankName is required for accounts at other banks.");
        }
        return new Details(name.replaceAll("\\s+", " "), accountNumber, bank, ifsc);
    }

    /** @param ownerId null for staff lookups, where the "own account" rule doesn't apply */
    private Account findInternalTarget(Long ownerId, String accountNumber) {
        Account target = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No CoreTrust account exists with this account number."));
        if (ownerId != null && target.getUser().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Your own accounts don't need to be added as beneficiaries.");
        }
        if (target.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE, "This account is closed.");
        }
        return target;
    }

    private void rejectFakeCoreTrustIfsc(String ifsc) {
        if (ifsc.startsWith(bankIfsc.substring(0, 4)) && !ifsc.equals(bankIfsc)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Invalid CoreTrust IFSC code. Use " + bankIfsc + ".");
        }
    }

    private Beneficiary createOrReactivate(User owner, Details details) {
        var existing = beneficiaryRepository.findByUserIdAndAccountNumber(owner.getId(), details.accountNumber());
        if (existing.isPresent()) {
            Beneficiary beneficiary = existing.get();
            if (beneficiary.getStatus() == BeneficiaryStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "This beneficiary has already been added.");
            }
            beneficiary.reactivate(details.name(), details.bankName(), details.ifsc());
            return beneficiary;
        }

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(owner);
        beneficiary.setBeneficiaryName(details.name());
        beneficiary.setAccountNumber(details.accountNumber());
        beneficiary.setBankName(details.bankName());
        beneficiary.setIfscCode(details.ifsc());
        return beneficiaryRepository.save(beneficiary);
    }

    private void ensureNotAlreadyActive(Long ownerId, String accountNumber) {
        beneficiaryRepository.findByUserIdAndAccountNumber(ownerId, accountNumber)
                .filter(b -> b.getStatus() == BeneficiaryStatus.ACTIVE)
                .ifPresent(b -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "This beneficiary has already been added.");
                });
    }

    private static String operation(Long customerId, Details d) {
        return ADD_BENEFICIARY_OPERATION.formatted(customerId, d.accountNumber(), d.ifsc(),
                d.name().toLowerCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private BeneficiaryResponse toResponse(Beneficiary b) {
        return BeneficiaryResponse.from(b, b.getIfscCode().equals(bankIfsc));
    }

    private void publishAdded(User owner, Beneficiary b, String addedBy) {
        eventPublisher.publishEvent(new BeneficiaryAddedEvent(owner.getName(), owner.getEmail(),
                b.getBeneficiaryName(), b.getAccountNumber(), b.getBankName(), addedBy));
    }

    private void publishRemoved(Beneficiary b, String removedBy) {
        User owner = b.getUser();
        eventPublisher.publishEvent(new BeneficiaryRemovedEvent(owner.getName(), owner.getEmail(),
                b.getBeneficiaryName(), b.getAccountNumber(), removedBy));
    }
}