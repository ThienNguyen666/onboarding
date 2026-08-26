import React, { useState, useCallback, useEffect } from "react";
import {
  Settings2, X, RefreshCw, Trash2, ChevronRight,
  ShieldCheck, ScanFace, Smartphone, KeyRound, FileText, CircleCheck,
  CircleX, CircleAlert, Camera, Radio, Wifi, BatteryFull, SignalHigh,
  Eye, ListTree, PlugZap, Check,
} from "lucide-react";
import { createRoot } from "react-dom/client";

/* ------------------------------------------------------------------ */
/*  API layer — gọi thẳng OnboardingController / DebugController thật */
/* ------------------------------------------------------------------ */

async function api(baseUrl, path, method = "GET", body) {
  let res;
  try {
    res = await fetch(`${baseUrl}${path}`, {
      method,
      headers: { "Content-Type": "application/json" },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new Error(
      `Không kết nối được tới ${baseUrl}. Kiểm tra: backend đã "./mvnw spring-boot:run" chưa? CORS đã bật (CorsConfig) chưa?`
    );
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error((data && data.message) || `HTTP ${res.status}`);
    err.code = data && data.code;
    err.status = res.status;
    throw err;
  }
  return data;
}

/* ------------------------------------------------------------------ */
/*  Phase map — song song với SessionPhase enum + swimlane             */
/* ------------------------------------------------------------------ */

const FLOW = [
  { key: "INIT", label: "Khởi tạo SDK", lane: "sys" },
  { key: "DEVICE_CHECK", label: "Thiết bị", lane: "you" },
  { key: "CUSTOMER_LOOKUP", label: "Số điện thoại", lane: "you" },
  { key: "OCR", label: "CCCD", lane: "you" },
  { key: "LIVENESS", label: "Khuôn mặt", lane: "you" },
  { key: "NFC", label: "Chip NFC", lane: "you" },
  { key: "IDENTITY_CONFIRM", label: "Xác nhận", lane: "you" },
  { key: "TNC", label: "Điều khoản", lane: "you" },
  { key: "OTP", label: "OTP", lane: "you" },
  { key: "ACCOUNT_CREATION", label: "Mở tài khoản", lane: "sys" },
  { key: "DONE", label: "Hoàn tất", lane: "sys" },
];

function phaseIndex(key) {
  const i = FLOW.findIndex((p) => p.key === key);
  return i === -1 ? 0 : i;
}

function randomId(prefix) {
  return `${prefix}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
}

/* ------------------------------------------------------------------ */
/*  Small UI atoms                                                     */
/* ------------------------------------------------------------------ */

function Field({ label, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
    </label>
  );
}

function PrimaryButton({ children, onClick, disabled, loading, tone = "primary" }) {
  return (
    <button
      className={`btn btn-${tone}`}
      onClick={onClick}
      disabled={disabled || loading}
    >
      {loading ? <span className="spinner" /> : children}
    </button>
  );
}

function Banner({ tone = "info", children }) {
  return <div className={`banner banner-${tone}`}>{children}</div>;
}

function StepShell({ icon, eyebrow, title, subtitle, children }) {
  return (
    <div className="card step-card">
      <div className="step-icon">{icon}</div>
      <div className="eyebrow">{eyebrow}</div>
      <h2>{title}</h2>
      {subtitle && <p className="step-sub">{subtitle}</p>}
      <div className="step-body">{children}</div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Main App                                                           */
/* ------------------------------------------------------------------ */

export default function App() {
  const [baseUrl, setBaseUrl] = useState(
    typeof window !== "undefined" ? window.location.origin : "http://localhost:8080"
  );
  const [sessionId, setSessionId] = useState(null);
  const [status, setStatus] = useState(null);
  const [extra, setExtra] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [devOpen, setDevOpen] = useState(false);
  const [devTab, setDevTab] = useState("controls");

  const [forceFail, setForceFail] = useState({ ocr: false, liveness: false, nfc: false });
  const [forceCompliance, setForceCompliance] = useState("");
  const [recent, setRecent] = useState([]);
  const [otpDebug, setOtpDebug] = useState(null);
  const [otpValue, setOtpValue] = useState("");

  const [form, setForm] = useState({
    vendorId: "VENDOR_BANK_APP",
    sdkSessionId: randomId("SDK"),
    productType: "TKTT_DEBIT",
    deviceModel: "Pixel 8 Pro",
    osVersion: "Android 15",
    nfcSupported: true,
    phone: "0909" + String(Math.floor(100000 + Math.random() * 900000)),
    tncChecked: false,
  });

  const setF = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const refreshStatus = useCallback(
    async (id) => {
      if (!id) return;
      try {
        const s = await api(baseUrl, `/api/onboarding/sessions/${id}`);
        setStatus(s);
      } catch (e) {
        /* transient — surfaced already by the action that triggered it */
      }
    },
    [baseUrl]
  );

  const run = async (fn) => {
    setError(null);
    setLoading(true);
    try {
      await fn();
    } catch (e) {
      setError(e.message || "Có lỗi xảy ra");
    } finally {
      setLoading(false);
    }
  };

  const resetLocal = () => {
    setSessionId(null);
    setStatus(null);
    setExtra({});
    setError(null);
    setNotice(null);
    setOtpValue("");
    setOtpDebug(null);
    setForm((f) => ({ ...f, sdkSessionId: randomId("SDK"), phone: "0909" + String(Math.floor(100000 + Math.random() * 900000)), tncChecked: false }));
  };

  /* ---- Phase 0 ---- */
  const doInit = () =>
    run(async () => {
      const res = await api(baseUrl, "/api/onboarding/sessions", "POST", {
        vendorId: form.vendorId,
        sdkSessionId: form.sdkSessionId,
        productType: form.productType,
      });
      setSessionId(res.sessionId);
      await refreshStatus(res.sessionId);
    });

  /* ---- Phase 1 ---- */
  const doDeviceCheck = () =>
    run(async () => {
      await api(baseUrl, `/api/onboarding/sessions/${sessionId}/device-check`, "POST", {
        model: form.deviceModel,
        osVersion: form.osVersion,
        nfcSupported: form.nfcSupported,
      });
      await refreshStatus(sessionId);
    });

  /* ---- Phase 2 ---- */
  const doLookup = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/customer-lookup`, "POST", {
        phone: form.phone,
      });
      setExtra((x) => ({ ...x, lookup: res }));
      if (res.dropoff) setNotice(`Đã khôi phục tiến trình cũ — tiếp tục từ bước "${labelOf(res.resumeStep)}"`);
      await refreshStatus(sessionId);
    });

  /* ---- Phase 3/4/5 ---- */
  const doEkyc = (step) =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/${step}`, "POST", {
        forceFail: !!forceFail[step],
        mockPayload: null,
      });
      setExtra((x) => ({ ...x, [step]: res }));
      await refreshStatus(sessionId);
    });

  /* ---- Phase 6a/b/c ---- */
  const doIdentityConfirm = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/identity-confirm`, "POST");
      setExtra((x) => ({ ...x, identity: res }));
      await refreshStatus(sessionId);
    });

  const doTnc = () =>
    run(async () => {
      await api(baseUrl, `/api/onboarding/sessions/${sessionId}/tnc`, "POST", { tncVersion: "NHD13-v1.0" });
      await refreshStatus(sessionId);
    });

  const doSendOtp = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/otp/send`, "POST");
      setExtra((x) => ({ ...x, otpSend: res }));
      setOtpDebug(null);
      setOtpValue("");
      await refreshStatus(sessionId);
    });

  const doVerifyOtp = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/otp/verify`, "POST", { otp: otpValue });
      setExtra((x) => ({ ...x, otpVerify: res }));
      await refreshStatus(sessionId);
      if (!res.verified) setError(`Sai OTP — còn ${res.attemptsLeft} lần thử`);
    });

  /* ---- Phase 7 ---- */
  const doCreateAccount = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/sessions/${sessionId}/account`, "POST", {
        forceComplianceResult: forceCompliance || null,
      });
      setExtra((x) => ({ ...x, account: res }));
      await refreshStatus(sessionId);
    });

  /* ---- Dev / QA console ---- */
  const doPeekOtp = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/debug/sessions/${sessionId}/otp`);
      setOtpDebug(res);
      setOtpValue(res.otp);
    });

  const doMarkDropoff = () =>
    run(async () => {
      await api(baseUrl, `/api/onboarding/sessions/${sessionId}/dropoff`, "POST");
      setNotice("Đã đánh dấu dropoff — thoát app. Bấm 'Mở tài khoản ngay' lại với CÙNG số điện thoại để test resume.");
      setSessionId(null);
      setStatus(null);
      setExtra({});
    });

  const doListRecent = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/onboarding/debug/sessions`);
      setRecent(res);
    });

  const doResetAll = () =>
    run(async () => {
      await api(baseUrl, `/api/onboarding/debug/reset`, "POST");
      setRecent([]);
      resetLocal();
      setNotice("Đã xoá toàn bộ session / audit log / OTP demo.");
    });

  useEffect(() => {
    if (devOpen && devTab === "sessions") doListRecent();
  }, [devOpen, devTab]);

  const [conductorWorkflowId, setConductorWorkflowId] = useState(null);
  const [conductorResult, setConductorResult] = useState(null);

  const doConductorStart = () =>
    run(async () => {
      const res = await api(baseUrl, "/api/conductor/start", "POST", {
        vendorClientId: "demo-client-id",
        vendorClientSecret: "demo-client-secret",
        sdkSessionId: form.sdkSessionId,
        productType: form.productType,
        deviceInfo: { model: form.deviceModel, osVersion: form.osVersion, nfcSupported: form.nfcSupported },
        phone: form.phone,
        otp: "",
        vendorId: form.vendorId,
        maxOcrRetries: 3,
        maxLivenessRetries: 3,
        maxNfcRetries: 3,
      });
      setConductorWorkflowId(res.workflowId);
      setConductorResult(null);
    });

  const doConductorStatus = () =>
    run(async () => {
      const res = await api(baseUrl, `/api/conductor/${conductorWorkflowId}`);
      setConductorResult(res);
    });

  const currentPhase = status ? status.phase : "INIT";
  const isTerminated = status && status.status !== "IN_PROGRESS";
  const isEtbRedirect = isTerminated && status.terminationReason && status.terminationReason.startsWith("ETB_REDIRECT");

  return (
    <div className="stage">
      <StyleBlock />

      <div className="topbar">
        <div className="topbar-brand">
          <div className="brand-mark">VB</div>
          <div>
            <div className="brand-title">VietBank eKYC — Demo Console</div>
            <div className="brand-sub mono">{baseUrl}</div>
          </div>
        </div>
        <button className="dev-fab-inline" onClick={() => setDevOpen(true)}>
          <Settings2 size={16} /> QA Console
        </button>
      </div>

      <div className="layout">
        {/* -------------------- PHONE -------------------- */}
        <div className="phone-wrap">
          <div className="phone-shell">
            <div className="phone-notch" />
            <div className="phone-screen">
              <FakeStatusBar />
              <AppHeader phone={form.phone} sessionId={sessionId} />
              <ProgressRail currentKey={currentPhase} terminated={isTerminated} />

              <div className="screen-content">
                {notice && (
                  <Banner tone="info">
                    {notice}
                    <button className="banner-close" onClick={() => setNotice(null)}><X size={13} /></button>
                  </Banner>
                )}
                {error && (
                  <Banner tone="danger">
                    {error}
                    <button className="banner-close" onClick={() => setError(null)}><X size={13} /></button>
                  </Banner>
                )}

                {!sessionId && (
                  <StepShell
                    icon={<PlugZap size={22} />}
                    eyebrow="Phase 0 · Khởi tạo SDK"
                    title="Mở tài khoản trong 5 phút"
                    subtitle="Xác nhận điều kiện mở TK bằng NHĐ13 để bắt đầu."
                  >
                    <Field label="Loại sản phẩm">
                      <select value={form.productType} onChange={(e) => setF("productType", e.target.value)}>
                        <option value="TKTT">Tài khoản thanh toán</option>
                        <option value="TKTT_DEBIT">TKTT + Thẻ Debit</option>
                      </select>
                    </Field>
                    <PrimaryButton onClick={doInit} loading={loading}>
                      Mở tài khoản ngay <ChevronRight size={16} />
                    </PrimaryButton>
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "DEVICE_CHECK" && (
                  <StepShell icon={<Smartphone size={22} />} eyebrow="Phase 1 · Thiết bị" title="Kiểm tra thiết bị">
                    <Field label="Dòng máy">
                      <input value={form.deviceModel} onChange={(e) => setF("deviceModel", e.target.value)} />
                    </Field>
                    <Field label="Hệ điều hành">
                      <input value={form.osVersion} onChange={(e) => setF("osVersion", e.target.value)} />
                    </Field>
                    <Toggle
                      label="Hỗ trợ đọc NFC"
                      checked={form.nfcSupported}
                      onChange={(v) => setF("nfcSupported", v)}
                    />
                    <PrimaryButton onClick={doDeviceCheck} loading={loading}>Tiếp tục</PrimaryButton>
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "CUSTOMER_LOOKUP" && (
                  <StepShell icon={<Radio size={22} />} eyebrow="Phase 2 · Định danh SĐT" title="Nhập số điện thoại">
                    <Field label="Số điện thoại">
                      <input
                        className="mono"
                        value={form.phone}
                        onChange={(e) => setF("phone", e.target.value)}
                        placeholder="0xxxxxxxxx"
                      />
                    </Field>
                    <p className="hint">Số kết thúc bằng 0901111111 / 0902222222 → demo nhánh khách hàng hiện hữu (ETB).</p>
                    <PrimaryButton onClick={doLookup} loading={loading}>Tiếp tục</PrimaryButton>
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "OCR" && (
                  <EkycStep
                    icon={<Camera size={22} />}
                    eyebrow="Phase 3 · OCR CCCD"
                    title="Chụp 2 mặt CCCD"
                    subtitle="Đưa CCCD vào khung hình, giữ yên trong 2 giây."
                    result={extra.ocr}
                    onSubmit={() => doEkyc("ocr")}
                    loading={loading}
                    actionLabel="Chụp CCCD"
                    forceFail={forceFail.ocr}
                  />
                )}

                {sessionId && !isTerminated && currentPhase === "LIVENESS" && (
                  <EkycStep
                    icon={<ScanFace size={22} />}
                    eyebrow="Phase 4 · Liveness"
                    title="Xác thực khuôn mặt"
                    subtitle="Nhìn thẳng vào camera và làm theo hướng dẫn."
                    result={extra.liveness}
                    onSubmit={() => doEkyc("liveness")}
                    loading={loading}
                    actionLabel="Bắt đầu quét"
                    forceFail={forceFail.liveness}
                  />
                )}

                {sessionId && !isTerminated && currentPhase === "NFC" && (
                  <EkycStep
                    icon={<Radio size={22} />}
                    eyebrow="Phase 5 · Chip NFC"
                    title="Chạm CCCD vào mặt sau điện thoại"
                    subtitle="Giữ nguyên vị trí cho tới khi đọc xong chip."
                    result={extra.nfc}
                    onSubmit={() => doEkyc("nfc")}
                    loading={loading}
                    actionLabel="Đọc chip NFC"
                    forceFail={forceFail.nfc}
                  />
                )}

                {sessionId && !isTerminated && currentPhase === "IDENTITY_CONFIRM" && (
                  <StepShell icon={<ShieldCheck size={22} />} eyebrow="Phase 6a · Xác nhận" title="Xác nhận thông tin định danh">
                    <div className="kv-card">
                      <KV label="Họ tên" value={extra.ocr?.passed !== undefined ? "NGUYEN VAN A" : "—"} />
                      <KV label="Số CCCD" value="079099001234" mono />
                      <KV label="Địa chỉ" value="123 Nguyễn Trãi, Q1, TP.HCM" />
                    </div>
                    <PrimaryButton onClick={doIdentityConfirm} loading={loading}>Thông tin chính xác</PrimaryButton>
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "TNC" && (
                  <StepShell icon={<FileText size={22} />} eyebrow="Phase 6b · Điều khoản" title="Điều khoản & điều kiện">
                    <div className="tnc-box">
                      Tôi đồng ý mở tài khoản thanh toán và (nếu có) thẻ ghi nợ theo Điều kiện điều khoản
                      NHĐ13 của ngân hàng, đồng ý các điều khoản về phí, bảo mật thông tin và sử dụng dịch vụ số.
                    </div>
                    <Toggle label="Tôi đã đọc và đồng ý" checked={form.tncChecked} onChange={(v) => setF("tncChecked", v)} />
                    <PrimaryButton onClick={doTnc} disabled={!form.tncChecked} loading={loading}>
                      Xác nhận & tiếp tục
                    </PrimaryButton>
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "OTP" && (
                  <StepShell icon={<KeyRound size={22} />} eyebrow="Phase 6c · OTP" title="Xác thực OTP">
                    {!extra.otpSend ? (
                      <PrimaryButton onClick={doSendOtp} loading={loading}>Gửi mã OTP</PrimaryButton>
                    ) : (
                      <>
                        <p className="hint">Mã OTP 6 số đã gửi tới {form.phone}, hết hạn sau {extra.otpSend.expiresInSeconds}s.</p>
                        <Field label="Nhập mã OTP">
                          <input
                            className="mono otp-input"
                            maxLength={8}
                            value={otpValue}
                            onChange={(e) => setOtpValue(e.target.value.replace(/\D/g, ""))}
                            placeholder="••••••"
                          />
                        </Field>
                        <PrimaryButton onClick={doVerifyOtp} loading={loading} disabled={!otpValue}>Xác nhận</PrimaryButton>
                        <button className="link-btn" onClick={doSendOtp} disabled={loading}>Gửi lại mã</button>
                      </>
                    )}
                  </StepShell>
                )}

                {sessionId && !isTerminated && currentPhase === "ACCOUNT_CREATION" && (
                  <StepShell icon={<ShieldCheck size={22} />} eyebrow="Phase 7 · Hoàn tất" title="Tạo tài khoản">
                    <p className="hint">Mọi bước xác thực đã xong. Bấm để đẩy hồ sơ vào hệ thống lõi.</p>
                    <PrimaryButton onClick={doCreateAccount} loading={loading}>Hoàn tất mở tài khoản</PrimaryButton>
                  </StepShell>
                )}

                {isTerminated && (
                  <ResultScreen
                    status={status}
                    extra={extra}
                    isEtbRedirect={isEtbRedirect}
                    onRestart={resetLocal}
                  />
                )}
              </div>
            </div>
          </div>
          <div className="phone-caption">Prototype UI — gọi trực tiếp REST API thật, không mock ở FE.</div>
        </div>

        {/* -------------------- SESSION PANEL (desktop) -------------------- */}
        <div className="side-panel">
          <div className="side-card">
            <div className="side-title">Trạng thái phiên</div>
            {status ? (
              <>
                <KV label="Session" value={status.sessionId} mono small />
                <KV label="Phase" value={status.phase} />
                <KV label="Status" value={<StatusBadge value={status.status} />} />
                <KV label="Customer type" value={status.customerType || "—"} />
                <KV label="Dropoff" value={status.dropoff ? "Có" : "Không"} />
                <KV label="Retry OCR/Liveness/NFC" value={`${status.ocrRetryCount}/${status.livenessRetryCount}/${status.nfcRetryCount}`} />
                {status.ebankUserId && <KV label="Ebank User" value={status.ebankUserId} mono small />}
                {status.accountNumber && <KV label="Số TK" value={status.accountNumber} mono />}
              </>
            ) : (
              <p className="hint">Chưa có phiên nào — bấm "Mở tài khoản ngay" trong app để bắt đầu.</p>
            )}
            <button className="link-btn" onClick={() => refreshStatus(sessionId)} disabled={!sessionId}>
              <RefreshCw size={13} /> Làm mới
            </button>
          </div>
        </div>
      </div>

      {/* -------------------- QA CONSOLE (drawer) -------------------- */}
      <div className={`dev-drawer ${devOpen ? "open" : ""}`}>
        <div className="dev-drawer-head">
          <div>
            <div className="dev-title">BACKSTAGE // QA CONSOLE</div>
            <div className="dev-sub">Dev tools — không hiển thị cho khách hàng thật</div>
          </div>
          <button className="icon-btn" onClick={() => setDevOpen(false)}><X size={18} /></button>
        </div>

        <div className="dev-tabs">
          <button className={devTab === "controls" ? "active" : ""} onClick={() => setDevTab("controls")}>Điều khiển</button>
          <button className={devTab === "sessions" ? "active" : ""} onClick={() => setDevTab("sessions")}>Sessions</button>
          <button className={devTab === "raw" ? "active" : ""} onClick={() => setDevTab("raw")}>Raw JSON</button>
          <button className={devTab === "orkes" ? "active" : ""} onClick={() => setDevTab("orkes")}>Orkes Cloud</button>
        </div>

        <div className="dev-body">
          {devTab === "controls" && (
            <>
              <div className="dev-section">
                <div className="dev-section-title">Kết nối</div>
                <input className="dev-input mono" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
              </div>

              <div className="dev-section">
                <div className="dev-section-title">Mock tung xà bần — ép lỗi từng bước eKYC</div>
                {["ocr", "liveness", "nfc"].map((k) => (
                  <Toggle
                    key={k}
                    label={`Ép FAIL bước ${k.toUpperCase()} lần thử tiếp theo`}
                    checked={forceFail[k]}
                    onChange={(v) => setForceFail((f) => ({ ...f, [k]: v }))}
                  />
                ))}
              </div>

              <div className="dev-section">
                <div className="dev-section-title">Ép kết quả compliance (Phase 7)</div>
                <select className="dev-input" value={forceCompliance} onChange={(e) => setForceCompliance(e.target.value)}>
                  <option value="">Auto (theo rule số cuối SĐT)</option>
                  <option value="SUCCESS">Ép SUCCESS</option>
                  <option value="NEED_REVIEW">Ép NEED_REVIEW</option>
                  <option value="FAILED">Ép FAILED</option>
                </select>
              </div>

              <div className="dev-section">
                <div className="dev-section-title">OTP</div>
                <button className="dev-btn" onClick={doPeekOtp} disabled={!sessionId || loading}>
                  <Eye size={14} /> Xem OTP hiện tại (debug endpoint)
                </button>
                {otpDebug && (
                  <div className="otp-peek mono">
                    {otpDebug.otp} <span className="hint">còn {otpDebug.ttlSecondsRemaining}s</span>
                  </div>
                )}
              </div>

              <div className="dev-section">
                <div className="dev-section-title">Dropoff</div>
                <button className="dev-btn" onClick={doMarkDropoff} disabled={!sessionId || loading}>
                  Giả lập thoát app giữa chừng (test resume)
                </button>
              </div>

              <div className="dev-section danger">
                <div className="dev-section-title">Nguy hiểm</div>
                <button className="dev-btn danger" onClick={doResetAll} disabled={loading}>
                  <Trash2 size={14} /> Xoá toàn bộ dữ liệu demo
                </button>
                <p className="hint">Xoá hết session, audit log, OTP trong Redis — dùng /api/onboarding/debug/reset.</p>
              </div>
            </>
          )}

          {devTab === "orkes" && (
            <div className="dev-section">
              <div className="dev-section-title">Chạy workflow THẬT trên Orkes Cloud</div>
              <p className="hint">
                Start workflow <code>vendor_sdk_ekyc_account_opening</code> trên Orkes Cloud (không phải state
                machine local). Yêu cầu: đã import workflow + task defs lên Orkes, backend chạy với
                CONDUCTOR_AUTH_KEY/SECRET hợp lệ và conductor.worker.auto-start=true.
              </p>
              <button className="dev-btn" onClick={doConductorStart} disabled={loading}>
                <PlugZap size={14} /> Start workflow trên Orkes Cloud
              </button>
              {conductorWorkflowId && (
                <>
                  <div className="otp-peek mono" style={{ fontSize: 12, wordBreak: "break-all" }}>
                    workflowId: {conductorWorkflowId}
                  </div>
                  <button className="dev-btn" onClick={doConductorStatus} disabled={loading}>
                    <RefreshCw size={14} /> Xem trạng thái
                  </button>
                </>
              )}
              {conductorResult && <pre className="raw-json">{JSON.stringify(conductorResult, null, 2)}</pre>}
            </div>
          )}

          {devTab === "sessions" && (
            <div className="dev-section">
              <button className="dev-btn" onClick={doListRecent} disabled={loading}>
                <ListTree size={14} /> Tải danh sách 20 session gần nhất
              </button>
              <div className="session-list">
                {recent.map((s) => (
                  <div key={s.sessionId} className="session-row mono">
                    <div>{s.sessionId.slice(0, 8)}…</div>
                    <div>{s.phoneMasked}</div>
                    <div>{s.phase}</div>
                    <StatusBadge value={s.status} small />
                  </div>
                ))}
                {recent.length === 0 && <p className="hint">Chưa tải hoặc chưa có session nào.</p>}
              </div>
            </div>
          )}

          {devTab === "raw" && (
            <div className="dev-section">
              <div className="dev-section-title">SessionStatusResponse</div>
              <pre className="raw-json">{status ? JSON.stringify(status, null, 2) : "// chưa có session"}</pre>
              <div className="dev-section-title">Response bước gần nhất</div>
              <pre className="raw-json">{JSON.stringify(extra, null, 2)}</pre>
            </div>
          )}
        </div>
      </div>
      {devOpen && <div className="dev-scrim" onClick={() => setDevOpen(false)} />}

      <button className="dev-fab" onClick={() => setDevOpen(true)} title="QA Console">
        <Settings2 size={20} />
      </button>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Sub components                                                     */
/* ------------------------------------------------------------------ */

function labelOf(phaseKey) {
  const p = FLOW.find((f) => f.key === phaseKey);
  return p ? p.label : phaseKey;
}

function Toggle({ label, checked, onChange }) {
  return (
    <button type="button" className={`toggle-row ${checked ? "on" : ""}`} onClick={() => onChange(!checked)}>
      <span>{label}</span>
      <span className="toggle-pill"><span className="toggle-dot" /></span>
    </button>
  );
}

function KV({ label, value, mono, small }) {
  return (
    <div className="kv-row">
      <span className="kv-label">{label}</span>
      <span className={`kv-value ${mono ? "mono" : ""} ${small ? "small" : ""}`}>{value}</span>
    </div>
  );
}

function StatusBadge({ value, small }) {
  const tone = value === "SUCCESS" ? "success" : value === "NEED_REVIEW" ? "review" : value === "FAILED" ? "danger" : "progress";
  return <span className={`badge badge-${tone} ${small ? "small" : ""}`}>{value}</span>;
}

function FakeStatusBar() {
  const [time, setTime] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 30000);
    return () => clearInterval(t);
  }, []);
  const hh = String(time.getHours()).padStart(2, "0");
  const mm = String(time.getMinutes()).padStart(2, "0");
  return (
    <div className="fake-status-bar">
      <span className="mono">{hh}:{mm}</span>
      <span className="status-icons"><SignalHigh size={13} /><Wifi size={13} /><BatteryFull size={15} /></span>
    </div>
  );
}

function AppHeader({ phone, sessionId }) {
  return (
    <div className="app-header">
      <div className="app-header-row">
        <div className="brand-mark small">VB</div>
        <div>
          <div className="app-header-title">VietBank Digital</div>
          <div className="app-header-sub">{sessionId ? `Phiên #${sessionId.slice(0, 8)}` : "Mở tài khoản số"}</div>
        </div>
      </div>
    </div>
  );
}

function ProgressRail({ currentKey, terminated }) {
  const idx = terminated ? FLOW.length - 1 : phaseIndex(currentKey);
  return (
    <div className="rail">
      <div className="rail-track">
        {FLOW.map((p, i) => (
          <div key={p.key} className={`rail-pill ${i < idx ? "done" : i === idx ? "active" : ""} lane-${p.lane}`}>
            <span className="rail-dot">{i < idx ? <Check size={10} /> : i + 1}</span>
            <span className="rail-label">{p.label}</span>
          </div>
        ))}
      </div>
      <div className="rail-legend">
        <span><i className="dot lane-you" /> Bạn thao tác</span>
        <span><i className="dot lane-sys" /> Hệ thống xử lý</span>
      </div>
    </div>
  );
}

function EkycStep({ icon, eyebrow, title, subtitle, result, onSubmit, loading, actionLabel, forceFail }) {
  const showRetry = result && !result.passed && result.status === "IN_PROGRESS";
  return (
    <StepShell icon={icon} eyebrow={eyebrow} title={title} subtitle={subtitle}>
      <div className="scan-frame">
        <div className="scan-corner tl" /><div className="scan-corner tr" />
        <div className="scan-corner bl" /><div className="scan-corner br" />
        {icon}
      </div>
      {forceFail && <p className="hint hint-warn">⚠ QA Console đang ép FAIL bước này.</p>}
      {showRetry && (
        <Banner tone="danger">
          Thất bại — lần thử {result.attempt}/{result.maxRetries}. {result.retryAllowed ? "Thử lại nhé." : "Hết lượt thử."}
        </Banner>
      )}
      <PrimaryButton onClick={onSubmit} loading={loading}>{actionLabel}</PrimaryButton>
    </StepShell>
  );
}

function ResultScreen({ status, extra, isEtbRedirect, onRestart }) {
  if (isEtbRedirect) {
    return (
      <StepShell icon={<CircleAlert size={22} />} eyebrow="Kết quả" title="Bạn đã có tài khoản">
        <Banner tone="info">Số điện thoại này đã là khách hàng hiện hữu (ETB). Vui lòng đăng nhập app để dùng tài khoản sẵn có.</Banner>
        <PrimaryButton onClick={onRestart}>Thử số khác</PrimaryButton>
      </StepShell>
    );
  }
  const tone = status.status === "SUCCESS" ? "success" : status.status === "NEED_REVIEW" ? "review" : "danger";
  const Icon = status.status === "SUCCESS" ? CircleCheck : status.status === "NEED_REVIEW" ? CircleAlert : CircleX;
  const title = status.status === "SUCCESS" ? "Mở tài khoản thành công!" : status.status === "NEED_REVIEW" ? "Hồ sơ đang được xét duyệt" : "Không thể mở tài khoản";
  return (
    <StepShell icon={<Icon size={22} />} eyebrow="Kết quả cuối cùng" title={title}>
      <div className={`result-panel result-${tone}`}>
        {status.accountNumber && <KV label="Số tài khoản" value={status.accountNumber} mono />}
        {status.ebankUserId && <KV label="Ebank User ID" value={status.ebankUserId} mono small />}
        {status.linkId && <KV label="Link ID" value={status.linkId} mono small />}
        {status.terminationReason && <KV label="Lý do" value={status.terminationReason} />}
      </div>
      <PrimaryButton onClick={onRestart}>Bắt đầu phiên mới</PrimaryButton>
    </StepShell>
  );
}

/* ------------------------------------------------------------------ */
/*  Styles                                                              */
/* ------------------------------------------------------------------ */

function StyleBlock() {
  return (
    <style>{`
      @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;600;700&family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');

      .stage{
        --navy:#0A2A4D; --navy-2:#123E6B; --teal:#0FB8A0; --teal-dk:#0A8E7C;
        --gold:#EFA23B; --red:#E5484D; --bg:#EEF2F8; --surface:#FFFFFF;
        --ink:#0F2138; --ink-soft:#5C6B82; --line:#E1E7F0;
        font-family:'Inter',sans-serif; background:var(--bg); color:var(--ink);
        min-height:100vh; padding:20px 16px 90px; box-sizing:border-box; position:relative;
      }
      .stage *{box-sizing:border-box;}
      .mono{font-family:'JetBrains Mono',monospace;}

      .topbar{display:flex; align-items:center; justify-content:space-between; max-width:1080px; margin:0 auto 22px;}
      .topbar-brand{display:flex; align-items:center; gap:10px;}
      .brand-mark{width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,var(--navy),var(--navy-2));
        color:#fff;display:flex;align-items:center;justify-content:center;font-family:'Space Grotesk';font-weight:700;font-size:13px;flex-shrink:0;}
      .brand-mark.small{width:28px;height:28px;font-size:11px;border-radius:8px;}
      .brand-title{font-family:'Space Grotesk';font-weight:700;font-size:15px;}
      .brand-sub{font-size:11px;color:var(--ink-soft);}
      .dev-fab-inline{display:flex;align-items:center;gap:6px;background:var(--navy);color:#fff;border:none;
        padding:8px 14px;border-radius:999px;font-size:12.5px;font-weight:600;cursor:pointer;}
      .dev-fab-inline:hover{background:var(--navy-2);}

      .layout{max-width:1080px;margin:0 auto;display:flex;gap:28px;justify-content:center;align-items:flex-start;flex-wrap:wrap;}

      /* Phone */
      .phone-wrap{display:flex;flex-direction:column;align-items:center;}
      .phone-shell{width:380px;max-width:92vw;background:linear-gradient(180deg,var(--navy),#081b33);
        border-radius:42px;padding:14px;box-shadow:0 30px 60px -20px rgba(10,42,77,.5);position:relative;}
      .phone-notch{position:absolute;top:14px;left:50%;transform:translateX(-50%);width:120px;height:22px;
        background:#081b33;border-radius:0 0 16px 16px;z-index:2;}
      .phone-screen{background:var(--surface);border-radius:30px;overflow:hidden;height:720px;
        display:flex;flex-direction:column;position:relative;}
      .phone-caption{margin-top:12px;font-size:11.5px;color:var(--ink-soft);text-align:center;max-width:320px;}

      .fake-status-bar{display:flex;justify-content:space-between;align-items:center;padding:14px 20px 4px;font-size:12px;color:var(--ink);}
      .status-icons{display:flex;gap:5px;align-items:center;color:var(--ink);}

      .app-header{background:linear-gradient(120deg,var(--navy),var(--navy-2));color:#fff;padding:10px 18px 16px;}
      .app-header-row{display:flex;align-items:center;gap:10px;}
      .app-header-title{font-family:'Space Grotesk';font-weight:700;font-size:14.5px;}
      .app-header-sub{font-size:11px;opacity:.75;font-family:'JetBrains Mono',monospace;}

      /* progress rail */
      .rail{background:var(--surface);padding:12px 14px 8px;border-bottom:1px solid var(--line);}
      .rail-track{display:flex;gap:6px;overflow-x:auto;padding-bottom:4px;scrollbar-width:none;}
      .rail-track::-webkit-scrollbar{display:none;}
      .rail-pill{display:flex;flex-direction:column;align-items:center;gap:4px;min-width:52px;opacity:.4;flex-shrink:0;}
      .rail-pill.active{opacity:1;}
      .rail-pill.done{opacity:.85;}
      .rail-dot{width:20px;height:20px;border-radius:50%;background:var(--line);display:flex;align-items:center;
        justify-content:center;font-size:10px;font-weight:700;color:var(--ink-soft);}
      .rail-pill.active .rail-dot{background:var(--teal);color:#fff;}
      .rail-pill.done .rail-dot{background:var(--navy-2);color:#fff;}
      .rail-label{font-size:8.5px;text-align:center;color:var(--ink-soft);line-height:1.15;max-width:56px;}
      .rail-pill.active .rail-label{color:var(--ink);font-weight:700;}
      .rail-legend{display:flex;gap:14px;font-size:10px;color:var(--ink-soft);margin-top:4px;}
      .rail-legend .dot{width:7px;height:7px;border-radius:50%;display:inline-block;margin-right:4px;}
      .dot.lane-you{background:var(--gold);} .dot.lane-sys{background:var(--navy-2);}
      .rail-pill.lane-sys.active .rail-dot{background:var(--navy-2);}
      .rail-pill.lane-you.active .rail-dot{background:var(--teal);}

      .screen-content{flex:1;overflow-y:auto;padding:18px 18px 26px;background:var(--bg);}

      .card{background:var(--surface);border-radius:18px;padding:20px;box-shadow:0 1px 3px rgba(15,33,56,.06);}
      .step-card{display:flex;flex-direction:column;gap:10px;}
      .step-icon{width:42px;height:42px;border-radius:12px;background:linear-gradient(135deg,var(--teal),var(--teal-dk));
        color:#fff;display:flex;align-items:center;justify-content:center;margin-bottom:2px;}
      .eyebrow{font-size:10.5px;letter-spacing:.06em;text-transform:uppercase;color:var(--teal-dk);font-weight:700;}
      .step-card h2{font-family:'Space Grotesk';font-size:19px;margin:0;}
      .step-sub{font-size:13px;color:var(--ink-soft);margin:0;}
      .step-body{display:flex;flex-direction:column;gap:12px;margin-top:4px;}

      .field{display:flex;flex-direction:column;gap:6px;}
      .field-label{font-size:12px;font-weight:600;color:var(--ink-soft);}
      .field input, .field select, .dev-input{border:1.5px solid var(--line);border-radius:10px;padding:10px 12px;
        font-size:14px;font-family:inherit;background:#fff;color:var(--ink);outline:none;}
      .field input:focus, .field select:focus, .dev-input:focus{border-color:var(--teal);}
      .otp-input{letter-spacing:6px;font-size:18px;text-align:center;}

      .btn{border:none;border-radius:12px;padding:13px 16px;font-size:14.5px;font-weight:700;cursor:pointer;
        display:flex;align-items:center;justify-content:center;gap:6px;font-family:inherit;transition:transform .1s;}
      .btn:active{transform:scale(.98);}
      .btn:disabled{opacity:.5;cursor:not-allowed;}
      .btn-primary{background:linear-gradient(120deg,var(--teal),var(--teal-dk));color:#fff;}
      .btn-secondary{background:#fff;border:1.5px solid var(--line);color:var(--ink);}
      .spinner{width:16px;height:16px;border-radius:50%;border:2.5px solid rgba(255,255,255,.4);
        border-top-color:#fff;animation:spin .7s linear infinite;}
      @keyframes spin{to{transform:rotate(360deg);}}

      .link-btn{background:none;border:none;color:var(--teal-dk);font-size:12.5px;font-weight:700;cursor:pointer;
        display:flex;align-items:center;gap:5px;padding:6px 0;}
      .link-btn:disabled{opacity:.4;cursor:not-allowed;}

      .hint{font-size:12px;color:var(--ink-soft);margin:0;}
      .hint-warn{color:var(--gold);font-weight:600;}

      .banner{border-radius:12px;padding:11px 14px;font-size:12.5px;display:flex;align-items:center;justify-content:space-between;gap:10px;}
      .banner-info{background:#E7F6F4;color:var(--teal-dk);}
      .banner-danger{background:#FDECEC;color:#B3261E;}
      .banner-close{background:none;border:none;cursor:pointer;color:inherit;opacity:.6;flex-shrink:0;}

      .toggle-row{width:100%;display:flex;align-items:center;justify-content:space-between;background:#F6F8FC;
        border:1.5px solid var(--line);border-radius:12px;padding:11px 13px;cursor:pointer;font-size:13px;color:var(--ink);}
      .toggle-pill{width:34px;height:19px;border-radius:999px;background:#CBD4E1;position:relative;flex-shrink:0;}
      .toggle-dot{position:absolute;top:2px;left:2px;width:15px;height:15px;border-radius:50%;background:#fff;transition:left .15s;}
      .toggle-row.on .toggle-pill{background:var(--teal);}
      .toggle-row.on .toggle-dot{left:17px;}

      .scan-frame{position:relative;height:150px;border-radius:14px;background:#0F2138;color:#5CE1D0;
        display:flex;align-items:center;justify-content:center;margin:2px 0;}
      .scan-corner{position:absolute;width:18px;height:18px;border-color:var(--teal);border-style:solid;}
      .scan-corner.tl{top:10px;left:10px;border-width:2px 0 0 2px;} .scan-corner.tr{top:10px;right:10px;border-width:2px 2px 0 0;}
      .scan-corner.bl{bottom:10px;left:10px;border-width:0 0 2px 2px;} .scan-corner.br{bottom:10px;right:10px;border-width:0 2px 2px 0;}

      .kv-card{background:#F6F8FC;border-radius:12px;padding:4px 14px;}
      .kv-row{display:flex;justify-content:space-between;gap:10px;padding:8px 0;border-bottom:1px solid var(--line);font-size:12.5px;}
      .kv-row:last-child{border-bottom:none;}
      .kv-label{color:var(--ink-soft);}
      .kv-value{font-weight:600;text-align:right;}
      .kv-value.small{font-size:11px;}

      .tnc-box{background:#F6F8FC;border-radius:12px;padding:14px;font-size:12px;color:var(--ink-soft);
        max-height:110px;overflow-y:auto;line-height:1.5;}

      .result-panel{border-radius:14px;padding:14px;}
      .result-success{background:#E7F6EE;} .result-review{background:#FDF3E3;} .result-danger{background:#FDECEC;}

      .badge{font-size:10px;font-weight:700;padding:3px 9px;border-radius:999px;letter-spacing:.02em;}
      .badge-success{background:#DFF5E8;color:#1E8A4C;} .badge-review{background:#FCEED2;color:#9A6300;}
      .badge-danger{background:#FBDCDC;color:#B3261E;} .badge-progress{background:#E1EAFB;color:#2554C7;}
      .badge.small{font-size:9px;padding:2px 7px;}

      /* side panel */
      .side-panel{width:280px;max-width:92vw;}
      .side-card{background:var(--surface);border-radius:16px;padding:16px;box-shadow:0 1px 3px rgba(15,33,56,.06);}
      .side-title{font-family:'Space Grotesk';font-weight:700;font-size:13px;margin-bottom:10px;color:var(--navy);}

      /* dev console */
      .dev-fab{position:fixed;right:22px;bottom:22px;width:52px;height:52px;border-radius:50%;
        background:var(--navy);color:#fff;border:none;box-shadow:0 12px 24px rgba(10,42,77,.35);cursor:pointer;
        display:flex;align-items:center;justify-content:center;z-index:40;}
      .dev-fab:hover{background:var(--navy-2);}
      .dev-scrim{position:fixed;inset:0;background:rgba(8,18,32,.4);z-index:45;}
      .dev-drawer{position:fixed;top:0;right:0;height:100%;width:360px;max-width:92vw;background:#0B1B2E;
        color:#DCE7F5;transform:translateX(100%);transition:transform .25s ease;z-index:50;display:flex;flex-direction:column;}
      .dev-drawer.open{transform:translateX(0);}
      .dev-drawer-head{display:flex;justify-content:space-between;align-items:flex-start;padding:18px 18px 12px;border-bottom:1px solid #1D3350;}
      .dev-title{font-family:'JetBrains Mono';font-weight:700;font-size:13px;color:#5CE1D0;letter-spacing:.03em;}
      .dev-sub{font-size:11px;color:#7C93B0;margin-top:3px;}
      .icon-btn{background:none;border:none;color:#7C93B0;cursor:pointer;}
      .dev-tabs{display:flex;gap:2px;padding:10px 14px 0;}
      .dev-tabs button{flex:1;background:none;border:none;color:#7C93B0;font-size:11.5px;font-weight:700;
        padding:9px 4px;cursor:pointer;border-bottom:2px solid transparent;}
      .dev-tabs button.active{color:#5CE1D0;border-bottom-color:#5CE1D0;}
      .dev-body{padding:14px 18px 28px;overflow-y:auto;flex:1;}
      .dev-section{margin-bottom:20px;}
      .dev-section-title{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.04em;color:#7C93B0;margin-bottom:8px;}
      .dev-section.danger .dev-section-title{color:#F49999;}
      .dev-input{width:100%;background:#132846;border:1.5px solid #1D3350;color:#DCE7F5;}
      .dev-btn{width:100%;background:#132846;border:1.5px solid #1D3350;color:#DCE7F5;border-radius:10px;
        padding:10px 12px;font-size:12.5px;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:7px;margin-bottom:6px;}
      .dev-btn:hover{border-color:#5CE1D0;}
      .dev-btn.danger{background:#3A1216;border-color:#5C1F24;color:#F49999;}
      .dev-btn:disabled{opacity:.4;cursor:not-allowed;}
      .otp-peek{margin-top:8px;background:#132846;border-radius:10px;padding:10px;font-size:20px;letter-spacing:4px;color:#5CE1D0;}
      .session-list{margin-top:10px;display:flex;flex-direction:column;gap:6px;}
      .session-row{display:grid;grid-template-columns:1fr 1fr 1fr auto;gap:6px;align-items:center;
        background:#132846;border-radius:8px;padding:8px 10px;font-size:10.5px;}
      .raw-json{background:#132846;border-radius:10px;padding:12px;font-size:10.5px;line-height:1.5;
        overflow-x:auto;white-space:pre-wrap;word-break:break-word;margin:0 0 16px;}

      @media (max-width:900px){
        .layout{flex-direction:column;align-items:center;}
        .side-panel{width:100%;max-width:380px;}
      }
    `}</style>
  );
}

const rootEl = document.getElementById("root");
if (rootEl) {
  createRoot(rootEl).render(<App />);
}