package com.github.ajalt.reprint.core;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.os.CancellationSignal;

import com.github.ajalt.reprint.module.marshmallow.MarshmallowReprintModule;

import java.lang.reflect.Constructor;

/**
 * Methods for performing fingerprint authentication.
 *
 * @hide
 */
enum ReprintInternal {
    INSTANCE;

    @Nullable
    private CancellationSignal cancellationSignal;

    @Nullable
    private ReprintModule module;

    public ReprintInternal initialize(Context context) {
        if (module != null) return this;

        // Load the spass module if it was included.
        try {
            final Class<?> spassModuleClass = Class.forName("com.github.ajalt.reprint.module.spass.SpassReprintModule");
            final Constructor<?> constructor = spassModuleClass.getConstructor(Context.class);
            ReprintModule module = (ReprintModule) constructor.newInstance(context);
            INSTANCE.registerModule(module);
        } catch (Exception ignored) {
        }

        registerModule(new MarshmallowReprintModule(context));

        return this;
    }

    public ReprintInternal registerModule(ReprintModule module) {
        if (this.module != null && module.tag() == this.module.tag()) {
            return this;
        }

        if (module.isHardwarePresent()) {
            this.module = module;
        }

        return this;
    }

    public boolean isHardwarePresent() {
        return module != null && module.isHardwarePresent();
    }

    public boolean hasFingerprintRegistered() {
        return module != null && module.hasFingerprintRegistered();
    }

    public void authenticate(final AuthenticationListener listener, int restartCount) {
        if (module == null || !module.isHardwarePresent() || !module.hasFingerprintRegistered()) {
            listener.onFailure(0, AuthenticationFailureReason.NO_HARDWARE, 0, null);
            return;
        }

        cancellationSignal = new CancellationSignal();
        module.authenticate(cancellationSignal, restartingListener(listener, restartCount));
    }

    public void cancelAuthentication() {
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    private AuthenticationListener restartingListener(final AuthenticationListener originalListener, final int restartCount) {
        return new AuthenticationListener() {
            @Override
            public void onSuccess() {
                originalListener.onSuccess();
            }

            @Override
            public void onFailure(int fromModule, AuthenticationFailureReason failureReason, int errorCode, @Nullable CharSequence errorMessage) {
                if (module != null && cancellationSignal != null &&
                        failureReason == AuthenticationFailureReason.TIMEOUT && restartCount > 0) {
                    module.authenticate(cancellationSignal, restartingListener(originalListener, restartCount - 1));
                } else {
                    originalListener.onFailure(fromModule, failureReason, errorCode, errorMessage);
                }
            }
        };
    }
}