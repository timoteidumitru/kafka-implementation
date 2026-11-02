package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayPolicyTest {

    private final ReplayPolicy policy = new ReplayPolicy();

    @Test
    void shouldRequireInvestigationWhenOriginalEventClaimIsMissing() {

        ReplayEvidence evidence = new ReplayEvidence(false);

        ReplayDecision decision = policy.evaluate(
                FailedEventType.INVENTORY_RESERVED,
                OrderStatus.COMPLETED,
                evidence
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }

    @Test
    void shouldRequireInvestigationWhenCommittedEventConflictsWithOrderState() {

        ReplayEvidence evidence = new ReplayEvidence(true);

        ReplayDecision decision = policy.evaluate(
                FailedEventType.INVENTORY_RESERVED,
                OrderStatus.CANCELLED,
                evidence
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }

    @Test
    void shouldResolveWithoutReplayWhenInventoryEventWasAlreadyProcessed() {

        ReplayEvidence evidence = new ReplayEvidence(
                true
        );

        ReplayDecision decision = policy.evaluate(
                FailedEventType.INVENTORY_RESERVED,
                OrderStatus.COMPLETED,
                evidence
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.RESOLVED_NO_REPLAY);
    }

    @Test
    void shouldRequireEvidenceBeforeReplayingAfterInfrastructureRecovery() {

        ReplayDecision decision = policy.evaluate(
                FailedEventType.INVENTORY_RESERVED,
                OrderStatus.CREATED
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }

    @Test
    void shouldBlockPaymentFailureReplayForCompletedOrder() {

        ReplayDecision decision = policy.evaluate(
                FailedEventType.PAYMENT_FAILED,
                OrderStatus.COMPLETED
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }

    @Test
    void shouldBlockInventorySuccessReplayForCancelledOrder() {

        ReplayDecision decision = policy.evaluate(
                FailedEventType.INVENTORY_RESERVED,
                OrderStatus.CANCELLED
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }

    @Test
    void shouldNotAutomaticallyApproveReplayForCreatedOrder() {

        ReplayDecision decision = policy.evaluate(
                FailedEventType.PAYMENT_FAILED,
                OrderStatus.CREATED
        );

        assertThat(decision)
                .isEqualTo(ReplayDecision.REQUIRES_INVESTIGATION);
    }
}
