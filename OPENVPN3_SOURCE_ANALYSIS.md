# OpenVPN 3 Source Code Analysis - Finding the Bug

## 🎯 Goal

Locate the exact bug in OpenVPN 3 that prevents `tun_send()` from being called, or find a configuration issue in our implementation.

---

## 📚 Source Code Investigation Results

### 1. Where `tun_send()` is Called

**File**: `openvpn/client/cliproto.hpp`

**Location 1** - Line 385-388 (data packet decryption):
```cpp
// make packet appear as incoming on tun interface
if (tun)
{
    OPENVPN_LOG_CLIPROTO("TUN send, size=" << buf.size());
    tun->tun_send(buf);
}
```

**Location 2** - Line 477 (MSS/MTU handling):
```cpp
if (df && c.mss_fix > 0 && buf.size() > mss_no_tcp_ip_encap)
{
    Ptb::generate_icmp_ptb(buf, ...);
    tun->tun_send(buf);
}
```

**Key Finding**: `tun_send()` is only called **if `tun` is not null**!

---

### 2. Where `tun` is Initialized

**File**: `openvpn/client/cliproto.hpp`  
**Function**: `recv_push_reply()` (line 1058)  
**Location**: Line 1108

**Critical Sequence**:
```cpp
// Line 1095-1109: Process PUSH_REPLY from server
// process pushed transport options
transport_factory->process_push(received_options);

// modify proto config (cipher, auth, key-derivation and compression methods)
proto_context.process_push(received_options, *proto_context_options);

// initialize tun/routing
tun = tun_factory->new_tun_client_obj(io_context, *this, transport.get());
tun->tun_start(received_options, *transport, proto_context.dc_settings());

// we should be connected at this point
if (!connected_)
    throw tun_exception("not connected");

// initialize data channel after pushed options have been processed
proto_context.init_data_channel();
```

**Key Finding**: The TUN client is created and started in `recv_push_reply()` AFTER the server sends configuration options.

---

### 3. The `connected_` Flag

**Requirement**: Line 1112 checks:
```cpp
if (!connected_)
    throw tun_exception("not connected");
```

This guard prevents reaching `init_data_channel()` if `connected_` is not set.

**Where `connected_` is Set**: Line 1209 in `tun_connected()` callback:
```cpp
void tun_connected() override
{
    OPENVPN_LOG("Connected via " + tun->tun_name());
    
    ClientEvent::Connected::Ptr ev = new ClientEvent::Connected();
    // ... populate event ...
    connected_ = std::move(ev);
}
```

**Key Finding**: `tun_connected()` is a **TunClientParent callback** that the TUN client MUST call to set the `connected_` flag!

---

### 4. Our Implementation Check

**File**: `app/src/main/cpp/custom_tun_client.h`  
**Function**: `tun_start()` - Lines 110-119

```cpp
extract_tun_config(opt);

// Notify parent
parent_.tun_pre_tun_config();
parent_.tun_pre_route_config();
start_async_read();  // CRITICAL: Start reading from lib_fd
parent_.tun_connected();  // ✅ WE CALL THIS!

OPENVPN_LOG("CustomTunClient started for tunnel: " << tunnel_id_);
```

**Log Verification**:
```
11:19:47.317 - Connected via custom_tun_nordvpn_UK  ✅
11:19:47.317 - CustomTunClient started for tunnel: nordvpn_UK  ✅
```

**Conclusion**: ✅ **We ARE calling `parent_.tun_connected()` correctly!**  
✅ **The `connected_` flag IS being set!**

---

### 5. How Outbound Packets Are Processed

**File**: `openvpn/client/cliproto.hpp`  
**Function**: `tun_recv()` - Lines 432-504

When we call `parent_.tun_recv(buf)` with an outbound packet:

```cpp
void tun_recv(BufferAllocated &buf) override
{
    OPENVPN_LOG_CLIPROTO("TUN recv, size=" << buf.size());
    
    // encrypt packet
    if (!buf.empty())
    {
        proto_context.data_encrypt(buf);  // Encrypt
        if (!buf.empty())
        {
            // send packet via transport to destination
            OPENVPN_LOG_CLIPROTO("Transport SEND...");
            if (transport->transport_send(buf))
                proto_context.update_last_sent();
        }
    }
}
```

**Process**:
1. Log "TUN recv" (we should see this in logs)
2. Encrypt the packet via `data_encrypt()`
3. Send encrypted packet via UDP using `transport->transport_send()`
4. Log "Transport SEND" (we should see this in logs)

**Key Finding**: If this works correctly, we should see both log messages!

---

### 6. How Inbound Packets Are Processed

**File**: `openvpn/client/cliproto.hpp`  
**Function**: `net_recv()` - Lines 354-400

When UDP transport receives encrypted data from server:

```cpp
void net_recv(BufferAllocated &buf)
{
    // get packet type
    ProtoContext::PacketType pt = proto_context.packet_type(buf);
    
    // process packet
    if (pt.is_data())
    {
        // data packet
        proto_context.data_decrypt(pt, buf);
        if (!buf.empty())
        {
            // make packet appear as incoming on tun interface
            if (tun)
            {
                OPENVPN_LOG_CLIPROTO("TUN send, size=" << buf.size());
                tun->tun_send(buf);
            }
        }
    }
}
```

**Process**:
1. Check packet type (data vs. control)
2. Decrypt data packet
3. **IF `tun` is not null**, call `tun->tun_send()` with decrypted packet
4. Log "TUN send"

**Key Finding**: If `tun` is null OR data channel isn't initialized, this will NOT call `tun_send()`!

---

## 🔍 Current Status

### What We Know Works ✅

1. ✅ `parent_.tun_connected()` is called
2. ✅ `connected_` flag is set
3. ✅ "Connected via custom_tun_nordvpn_UK" is logged
4. ✅ `tun` variable should be set (line 1108 runs)
5. ✅ `init_data_channel()` should be reached (line 1119)

### What We Need to Verify ❓

1. ❓ Is `tun_recv()` being called with our outbound packets?
2. ❓ Is `data_encrypt()` successfully encrypting packets?
3. ❓ Is `transport->transport_send()` sending encrypted packets via UDP?
4. ❓ Is the server responding with encrypted data?
5. ❓ Is `net_recv()` being called with server responses?
6. ❓ Is `data_decrypt()` successfully decrypting responses?
7. ❓ Is `tun` still non-null when `net_recv()` tries to call `tun_send()`?

---

## 🛠️ Debug Strategy

### Enabled: OPENVPN_DEBUG_CLIPROTO

We've enabled detailed protocol logging which will show:

```cpp
OPENVPN_LOG_CLIPROTO("TUN recv, size=...");  // When we feed packets
OPENVPN_LOG_CLIPROTO("Transport SEND...");   // When OpenVPN sends via UDP
OPENVPN_LOG_CLIPROTO("TUN send, size=...");  // When OpenVPN calls tun_send()
```

### Next Test Run Will Show

**Scenario A**: Logs show "TUN recv" and "Transport SEND"
- **Meaning**: OpenVPN IS encrypting and sending our packets ✅
- **Problem**: Either server not responding, or responses not being decrypted

**Scenario B**: Logs show "TUN recv" but NO "Transport SEND"
- **Meaning**: Encryption fails or transport layer issue
- **Problem**: Data channel not properly initialized

**Scenario C**: NO "TUN recv" logs
- **Meaning**: Our `parent_.tun_recv()` calls aren't reaching OpenVPN
- **Problem**: Something wrong with our async I/O or buffer passing

**Scenario D**: Logs show "TUN recv", "Transport SEND", but never "TUN send"
- **Meaning**: Outbound works, inbound doesn't
- **Problem**: Either no server response, or `tun` is null when `net_recv()` runs

---

## 💡 Potential Issues to Investigate

### Issue 1: Data Channel Not Initialized

**Hypothesis**: `proto_context.init_data_channel()` (line 1119) might be failing silently.

**Check**: Look for any errors or exceptions during initialization.

**Solution**: Add logging before/after `init_data_channel()` call.

---

### Issue 2: TUN Object Becomes Null

**Hypothesis**: The `tun` pointer might be valid during `tun_start()` but becomes null later when `net_recv()` tries to use it.

**Check**: Verify `tun` pointer persistence throughout connection lifetime.

**Solution**: Add logging in `net_recv()` to check `if (!tun)` condition.

---

### Issue 3: Async I/O Registration Issue

**Hypothesis**: Our `start_async_read()` registers `lib_fd` with OpenVPN's `io_context`, but OpenVPN might not be polling it correctly.

**Evidence**: Our unit tests prove bidirectional flow works perfectly.

**Check**: Verify OpenVPN's event loop is running and polling our FD.

**Solution**: Add logging in `handle_read()` to confirm async reads are happening.

---

### Issue 4: Data Channel Keys Not Generated

**Hypothesis**: `init_data_channel()` checks `if (!data_channel_key) return;` (line 2293 in proto.hpp). If keys aren't ready, data channel won't initialize.

**Check**: Verify `data_channel_key` is set before `init_data_channel()` is called.

**Solution**: Add logging in proto.hpp to check key status.

---

## 🎯 Recommended Next Steps

### Step 1: Run Test with CLIPROTO Debug Logging ⏱️ 5 min

Run E2E test and capture logs showing:
- "TUN recv" messages (proves we're feeding packets)
- "Transport SEND" messages (proves encryption/sending works)
- "TUN send" messages (proves inbound decryption works)

### Step 2: Analyze Log Patterns ⏱️ 10 min

Based on which logs appear, determine exact failure point:
- No "TUN recv" → Problem in our code
- "TUN recv" but no "Transport SEND" → Data channel init issue
- Both but no "TUN send" → Server not responding OR `tun` null check

### Step 3: Add Targeted Logging ⏱️ 15 min

Based on Step 2 findings, add specific logging:
- Before/after `init_data_channel()`
- In `net_recv()` to check `tun` pointer
- In `data_decrypt()` to check if decryption succeeds

### Step 4: Identify Root Cause ⏱️ 20 min

With detailed logs, pinpoint the exact line where the flow breaks:
- Configuration issue (missing flag/option)
- Timing issue (race condition)
- OpenVPN 3 bug (incompatibility with External TUN Factory)

### Step 5: Implement Workaround or Fix ⏱️ Variable

Based on root cause:
- **If config issue**: Fix our configuration
- **If timing issue**: Add synchronization
- **If OpenVPN 3 bug**: Report bug and consider OpenVPN 2

---

## 📊 Investigation Progress

**Completed** ✅:
- Found where `tun_send()` is called in OpenVPN 3 source
- Found where `tun` is initialized
- Verified our `tun_connected()` callback is being called
- Enabled detailed protocol logging (CLIPROTO)
- Understood complete packet flow (outbound & inbound)

**Next** ⏳:
- Run test with CLIPROTO logging
- Analyze which logs appear/don't appear
- Pinpoint exact failure point
- Implement fix or workaround

---

**Status**: Ready for detailed log analysis  
**Blocker**: Need to run test with CLIPROTO enabled  
**Timeline**: 30-60 minutes to root cause identification


