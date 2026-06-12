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

// JNI bridge to the tgcalls GROUP engine (GroupInstanceCustomImpl), powering
// video chats / voice chats. Mirrors the 1:1 bridge in tgvoip.cpp.
//
// Slice 2a (this file, initial): minimal lifecycle — create the native group
// instance, toggle self-mute, and stop it. The join-payload handshake
// (emitJoinPayload / setJoinResponsePayload), participant volume, and audio-level
// callbacks are added in subsequent slices.

#include <jni_utils.h>
#include "bridge.h"

#include <tgcalls/group/GroupInstanceCustomImpl.h>
#include <tgcalls/StaticThreads.h>

#include <memory>
#include <utility>

namespace {
  // Owns the native group-call instance for the lifetime of one joined call.
  struct GroupCallContext {
    std::unique_ptr<tgcalls::GroupInstanceInterface> instance;
  };
}

// Creates a group-call instance. Returns an opaque native pointer (0 on failure).
JNI_OBJECT_FUNC(jlong, voip_GroupCallInstance, newInstance, jboolean muted) {
  tgcalls::GroupInstanceDescriptor descriptor;
  descriptor.threads = tgcalls::StaticThreads::getThreads();
  descriptor.isConference = false;
  descriptor.useDummyChannel = true;

  auto *context = new GroupCallContext();
  context->instance = std::make_unique<tgcalls::GroupInstanceCustomImpl>(std::move(descriptor));
  context->instance->setIsMuted(muted == JNI_TRUE);

  return jni::ptr_to_jlong(context);
}

// Toggles the local microphone mute state.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, setMuted, jlong ptr, jboolean muted) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context != nullptr && context->instance != nullptr) {
    context->instance->setIsMuted(muted == JNI_TRUE);
  }
}

// Stops the instance and releases all native resources.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, stopNative, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr) {
    return;
  }
  if (context->instance != nullptr) {
    context->instance->stop([] {});
  }
  delete context;
}
