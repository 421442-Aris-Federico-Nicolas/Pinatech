package com.computerstore.payment.service;

import java.util.UUID;

record ReconciliationInstruction(UUID attemptId, String preferenceId) {
}
