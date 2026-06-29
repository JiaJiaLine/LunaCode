package com.lunacode.interaction;

public interface PermissionConfirmationBroker {
    PermissionConfirmationAnswer confirm(PermissionConfirmationRequest request);
}
