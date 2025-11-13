# Code Style & Comment Guidelines

**Date**: 2025-11-07  
**Purpose**: Professional, direct code comments  
**Status**: In Progress

---

## 🎯 Comment Philosophy

### **DO: Be Direct and Descriptive**
```cpp
// Provides custom TUN implementation via ExternalTun::Factory interface
// Allows the application to control packet I/O for multi-tunnel routing
```

### **DON'T: Be Defensive or Comparative**
```cpp
// This is the CORRECT way to provide custom TUN implementations
// Instead of hacking TunBuilderBase, we implement ExternalTun::Factory
```

---

## 📝 Comment Style Guide

### **Function/Class Documentation**

**Good**:
```cpp
/**
 * Creates a TunClientFactory for custom TUN implementation.
 * 
 * OpenVPN 3 calls this method to obtain a factory for creating TUN clients.
 * Returns a CustomTunClientFactory which creates CustomTunClient instances.
 */
```

**Avoid**:
```cpp
/**
 * CRITICAL: This is the main entry point for external TUN factory.
 * We return a CustomTunClientFactory which will create CustomTunClient.
 * NOTE: This is the CORRECT way to do this!
 */
```

---

### **Inline Comments**

**Good**:
```cpp
parent_.tun_pre_tun_config();
parent_.tun_pre_route_config();
start_async_read();  // Start async reading from lib_fd
parent_.tun_connected();
```

**Avoid**:
```cpp
start_async_read();  // CRITICAL: Start reading from lib_fd to feed packets to OpenVPN
```

---

### **Architecture Explanations**

**Good**:
```cpp
// Inbound path: server → OpenVPN → app
// Receives decrypted packets from OpenVPN and delivers to application
```

**Avoid**:
```cpp
// CRITICAL LOGGING: Log EVERY call to tun_send to verify OpenVPN is calling it
// This is the INBOUND path (server → OpenVPN → app)
```

---

## 🚫 Words to Avoid

### **Urgency Markers**
- ❌ CRITICAL:
- ❌ IMPORTANT:
- ❌ WARNING:
- ❌ NOTE: (use sparingly)

**Why**: Makes code feel fragile and defensive

**Instead**: Just state what the code does

---

### **Comparative Language**
- ❌ "Instead of..."
- ❌ "Don't..."
- ❌ "Avoid..."
- ❌ "The CORRECT way..."
- ❌ "The WRONG way..."

**Why**: Assumes reader is considering alternatives

**Instead**: State the current approach directly

---

### **Defensive Phrasing**
- ❌ "We need to..."
- ❌ "We must..."
- ❌ "Make sure to..."
- ❌ "Be careful to..."

**Why**: Sounds uncertain or apologetic

**Instead**: State what happens confidently

---

## ✅ Examples of Good Comments

### **1. API Usage**
```cpp
// OpenVPN 3 calls new_tun_factory() to obtain a factory
```
Not: ~~"CRITICAL: new_tun_factory() MUST be called by OpenVPN 3"~~

### **2. Data Flow**
```cpp
// Outbound path: app → OpenVPN → server
```
Not: ~~"CRITICAL: This implements the OUTBOUND path"~~

### **3. Memory Management**
```cpp
// Factory pointer managed by OpenVPN 3
```
Not: ~~"NOTE: This is a NON-OWNING pointer - OpenVPN 3 owns it and will delete it"~~

### **4. Configuration**
```cpp
// Auto-detect logging level from build type
```
Not: ~~"IMPORTANT: If not set, we try to infer it"~~

### **5. Error Handling**
```cpp
// Continue processing subsequent packets
```
Not: ~~"Still queue next read to avoid getting stuck"~~

---

## 📊 Comment Cleanup Progress

### **Completed** ✅
- [x] external_tun_factory.h - Header comment cleaned
- [x] custom_tun_client.h - Header comments cleaned
- [x] Logging macros created (logging_config.h)

### **In Progress** ⏳
- [ ] openvpn_wrapper.cpp (~200 comments to review)
- [ ] openvpn_jni.cpp (~40 comments to review)
- [ ] CMakeLists.txt (~100 comments to review)
- [ ] android_tun_builder.cpp (~50 comments to review)

### **Strategy**
Update comments gradually:
1. Start with user-facing headers
2. Move to implementation files
3. Focus on most visible comments first
4. Test after each batch

---

## 🎓 Best Practices

### **When to Comment**

**DO Comment**:
- ✅ Complex algorithms
- ✅ Non-obvious design decisions
- ✅ Public API documentation
- ✅ Architecture patterns
- ✅ Performance considerations

**DON'T Comment**:
- ❌ Obvious code (`i++; // increment i`)
- ❌ Restating variable names
- ❌ Defensive justifications
- ❌ Version history (use git)

---

### **Comment Length**

**Good**:
```cpp
// Allocate buffer with 256 bytes headroom for encryption headers
```

**Too Short**:
```cpp
// Buffer
```

**Too Long**:
```cpp
// CRITICAL: Allocate buffer with headroom for encryption headers!
// OpenVPN needs space at the front of the buffer to add encryption overhead.
// Typically needs 128-256 bytes of headroom for:
// - Protocol headers (up to 100 bytes)
// - Encryption/HMAC overhead (up to 100 bytes)  
// - Alignment padding
// We use 256 bytes to be safe!
```

---

## 🔍 Self-Review Checklist

Before committing, check your comments:

- [ ] No CRITICAL/IMPORTANT/WARNING prefixes?
- [ ] No "Instead of..." or "Don't..."?
- [ ] States WHAT, not WHY NOT?
- [ ] Direct and confident tone?
- [ ] Appropriate length?
- [ ] Adds value (not obvious)?

---

## 📚 References

### **Good Resources**:
- Google C++ Style Guide (Comments section)
- Linux Kernel Coding Style
- LLVM Coding Standards

### **Key Principles**:
1. **Self-Documenting Code First** - Good names > comments
2. **Explain Why, Not What** - Code shows what, comments explain why
3. **Be Concise** - Shorter is usually better
4. **Be Confident** - Direct statements, not defensive

---

## 🎯 Migration Plan

### **Phase 1: Headers** (Completed ✅)
- external_tun_factory.h
- custom_tun_client.h (partial)

### **Phase 2: Implementation** (Next)
- openvpn_wrapper.cpp
- openvpn_jni.cpp

### **Phase 3: Build System** (Future)
- CMakeLists.txt
- Build scripts

### **Phase 4: Kotlin** (Future)
- VpnConnectionManager.kt
- PacketRouter.kt
- VpnEngineService.kt

---

**Status**: ✅ Guidelines established, initial cleanup complete  
**Next**: Continue with implementation files  
**Goal**: Professional, production-ready comments throughout


