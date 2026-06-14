/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// Shared layout for the camera-capture context handed across the JNI boundary as
// an opaque jlong. Both the 1:1 bridge (tgvoip.cpp, voip_TgCallsController) and the
// group bridge (group_call.cpp, voip_GroupCallInstance) create / consume the SAME
// VideoCaptureContext so a capturer can be attached to either kind of instance.
//
// Owns one camera VideoCaptureInterface plus the AndroidContext (PlatformContext)
// it was created with. The AndroidContext instantiates the Java
// org.telegram.messenger.voip.VideoCameraCapturer and must outlive the capturer,
// so both are kept together and torn down in destructor order (capture first).

#ifndef TGX_VIDEO_CAPTURE_CONTEXT_H
#define TGX_VIDEO_CAPTURE_CONTEXT_H

#ifndef DISABLE_TGCALLS

#include <memory>

#include <tgcalls/VideoCaptureInterface.h>
#include <platform/android/AndroidContext.h>

struct VideoCaptureContext {
  std::shared_ptr<tgcalls::PlatformContext> platformContext;
  std::shared_ptr<tgcalls::VideoCaptureInterface> capture;
};

#endif // DISABLE_TGCALLS

#endif // TGX_VIDEO_CAPTURE_CONTEXT_H
