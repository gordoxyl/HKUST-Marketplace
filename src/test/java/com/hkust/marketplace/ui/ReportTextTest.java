package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.ReportReason;
import com.hkust.marketplace.services.ReportForm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link ReportText}: the wording of the report form (UC9 steps 3 and 9, Special Requirements 2 and 4). */
class ReportTextTest {

    private final ReportForm form = new ReportForm("L1", "Desk Lamp", List.of(ReportReason.values()));

    @Test
    void theFormsHeading_namesTheListing() { // step 3
        assertEquals("Report 'Desk Lamp' to the admin", ReportText.formHeader(form));
    }

    @Test
    void theConfirmation_namesTheListing() { // step 9
        assertEquals("Your report of 'Desk Lamp' was submitted. The admin will review the listing.",
                ReportText.submitted(form));
    }

    @Test
    void theHint_saysDetailsAreRequired_andTheSellerIsNeverToldWhoReported() { // Special Requirements 2 and 4
        assertTrue(ReportText.HINT.startsWith("Details are required for every reason."));
        assertTrue(ReportText.HINT.contains("the seller is never told who reported a listing"));
    }
}
