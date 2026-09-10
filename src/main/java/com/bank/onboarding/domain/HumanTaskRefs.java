package com.bank.onboarding.domain;

import java.util.Map;
import java.util.Set;

/**
 * Danh sách các human-task ref (asyncComplete=true) — dùng chung giữa WorkflowStatusMapper,
 * ConductorController (/meta) và FE (fetch qua API thay vì hardcode trùng lặp).
 */
public final class HumanTaskRefs {

      public static final Set<String> REFS = Set.of(
                  "show_cvp_ref", "collect_phone_number_ref",
                  "loop_perform_ocr_ref", "loop_perform_liveness_ref",
                  "loop_perform_nfc_ref", "verify_otp_ref",
                  "show_identity_confirmation_ref", "show_tnc_screen_ref");

      public static final Map<String, String> TO_LOOP_REF = Map.of(
                  "loop_perform_ocr_ref", "ocr_cccd_retry_loop_ref",
                  "loop_perform_liveness_ref", "liveness_retry_loop_ref",
                  "loop_perform_nfc_ref", "nfc_retry_loop_ref");

      private HumanTaskRefs() {}
}