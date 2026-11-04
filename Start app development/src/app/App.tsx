import { useState } from "react"
import {
  Truck, MapPin, Search, Camera, ChevronRight, Phone, MessageCircle,
  Star, Battery, Wifi, Signal, Package, Clock, TrendingUp, Users,
  Activity, BarChart2, Map, Settings, LogOut, Bell, RefreshCw,
  CheckCircle, Navigation, Radio, AlertCircle,
} from "lucide-react"
import {
  AreaChart, Area, BarChart, Bar, XAxis, YAxis,
  ResponsiveContainer, Tooltip,
} from "recharts"

// ─── Data ────────────────────────────────────────────────────────────────────

const weeklyTons = [
  { day: "Mon", tons: 12.4 }, { day: "Tue", tons: 18.7 },
  { day: "Wed", tons: 15.2 }, { day: "Thu", tons: 22.1 },
  { day: "Fri", tons: 19.8 }, { day: "Sat", tons: 25.3 },
  { day: "Sun", tons: 14.6 },
]

const hourlyRequests = [
  { hour: "6am", n: 8 }, { hour: "8am", n: 24 }, { hour: "10am", n: 18 },
  { hour: "12pm", n: 32 }, { hour: "2pm", n: 28 }, { hour: "4pm", n: 41 },
  { hour: "6pm", n: 35 }, { hour: "8pm", n: 12 },
]

const liveRequests = [
  { id: "REQ-4821", location: "14 Adeola Odeku St, VI", status: "pending",   time: "2s ago",  volume: "3 bags",      payout: "₦2,400" },
  { id: "REQ-4820", location: "Lekki Phase 1, Block 7", status: "en-route",  time: "1m ago",  volume: "Half truck",  payout: "₦8,500" },
  { id: "REQ-4819", location: "Marina Waterfront, VI",  status: "completed", time: "4m ago",  volume: "2 bags",      payout: "₦1,800" },
  { id: "REQ-4818", location: "Ikoyi Close, No. 3",     status: "completed", time: "7m ago",  volume: "Full truck",  payout: "₦15,000" },
  { id: "REQ-4817", location: "Gbagada Phase 2",         status: "pending",   time: "9m ago",  volume: "5 bags",      payout: "₦4,000" },
  { id: "REQ-4816", location: "Surulere Stadium Rd",    status: "en-route",  time: "11m ago", volume: "3 bags",      payout: "₦2,400" },
  { id: "REQ-4815", location: "Yaba Tech Campus",        status: "completed", time: "15m ago", volume: "Half truck",  payout: "₦8,500" },
  { id: "REQ-4814", location: "Ikeja GRA, Block B",     status: "pending",   time: "18m ago", volume: "2 bags",      payout: "₦1,800" },
]

const fleet = [
  { id: "TRK-01", driver: "Emeka Okafor",   load: 75, status: "en-route", zone: "Victoria Island" },
  { id: "TRK-02", driver: "Funmi Adeyemi",  load: 30, status: "idle",     zone: "Lekki Phase 1" },
  { id: "TRK-03", driver: "Chidi Nwosu",    load: 90, status: "en-route", zone: "Ikoyi" },
  { id: "TRK-04", driver: "Bola Kuti",      load: 15, status: "idle",     zone: "Gbagada" },
  { id: "TRK-05", driver: "Adaora Metu",    load: 60, status: "en-route", zone: "Surulere" },
]

// ─── Shared helpers ───────────────────────────────────────────────────────────

const glass = (extra = "") =>
  `backdrop-blur-xl bg-[rgba(13,18,28,0.88)] border border-[rgba(255,255,255,0.08)] ${extra}`

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    pending:   "bg-yellow-400/10 text-yellow-400 border-yellow-400/20",
    "en-route":"bg-emerald-400/10 text-emerald-400 border-emerald-400/20",
    completed: "bg-blue-400/10 text-blue-400 border-blue-400/20",
    idle:      "bg-white/5 text-white/30 border-white/10",
  }
  const labels: Record<string, string> = { "en-route": "En Route" }
  return (
    <span className={`inline-block text-[9px] font-bold tracking-widest px-2 py-0.5 rounded-full border uppercase ${map[status] ?? map.idle}`}
          style={{ fontFamily: "DM Mono, monospace" }}>
      {labels[status] ?? status}
    </span>
  )
}

// ─── Fake map SVG ─────────────────────────────────────────────────────────────

function MapBg({ variant = "city" }: { variant?: "city" | "radar" | "nav" }) {
  return (
    <div className="absolute inset-0 overflow-hidden"
         style={{ background: variant === "radar" ? "#08101a" : "#0d1621" }}>
      <svg className="w-full h-full" viewBox="0 0 320 580" preserveAspectRatio="xMidYMid slice">
        <defs>
          <pattern id={`g${variant}`} width="36" height="36" patternUnits="userSpaceOnUse">
            <path d="M36 0L0 0 0 36" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="0.4" />
          </pattern>
          {variant === "radar" && (
            <radialGradient id="radarPulse" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#22c55e" stopOpacity="0.12" />
              <stop offset="100%" stopColor="#22c55e" stopOpacity="0" />
            </radialGradient>
          )}
          {variant === "nav" && (
            <filter id="glow">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
            </filter>
          )}
        </defs>
        <rect width="320" height="580" fill={`url(#g${variant})`} />

        {/* Major roads */}
        <path d="M0,210 Q80,200 160,215 Q240,230 320,215" stroke="rgba(255,255,255,0.11)" strokeWidth="10" fill="none" />
        <path d="M0,340 Q100,328 160,340 Q240,352 320,340" stroke="rgba(255,255,255,0.14)" strokeWidth="14" fill="none" />
        <path d="M90,0 Q96,145 90,290 Q84,435 90,580"       stroke="rgba(255,255,255,0.09)" strokeWidth="8"  fill="none" />
        <path d="M215,0 Q222,145 215,290 Q208,435 215,580"  stroke="rgba(255,255,255,0.09)" strokeWidth="8"  fill="none" />
        <path d="M0,470 Q160,460 320,470"                   stroke="rgba(255,255,255,0.06)" strokeWidth="6"  fill="none" />

        {/* City blocks */}
        {([
          [10,40,68,80],[100,40,100,80],[220,40,90,80],
          [10,155,68,40],[100,155,100,40],[220,155,90,40],[320-50,155,40,40],
          [10,255,68,60],[100,255,100,60],[220,255,90,60],[320-50,255,60,60],
          [10,360,68,90],[100,360,100,70],[220,360,90,70],[320-50,360,40,70],
        ] as [number,number,number,number][]).map(([x,y,w,h],i) => (
          <rect key={i} x={x} y={y} width={w} height={h} rx="3" fill="rgba(255,255,255,0.025)" />
        ))}

        {/* Radar rings */}
        {variant === "radar" && (
          <>
            <circle cx="160" cy="290" r="75"  fill="none" stroke="rgba(34,197,94,0.18)" strokeWidth="1" />
            <circle cx="160" cy="290" r="135" fill="none" stroke="rgba(34,197,94,0.1)"  strokeWidth="1" />
            <circle cx="160" cy="290" r="195" fill="none" stroke="rgba(34,197,94,0.06)" strokeWidth="1" />
            <rect width="320" height="580" fill="url(#radarPulse)" />
          </>
        )}

        {/* Nav route */}
        {variant === "nav" && (
          <path d="M155,520 Q148,400 152,300 Q158,200 150,110"
                stroke="#22c55e" strokeWidth="5" fill="none"
                strokeDasharray="10 5" strokeLinecap="round"
                filter="url(#glow)" opacity="0.9" />
        )}
      </svg>
    </div>
  )
}

// ─── Phone frame wrapper ──────────────────────────────────────────────────────

function PhoneFrame({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3">
      <p className="text-[10px] font-bold text-white/25 tracking-[0.2em] uppercase"
         style={{ fontFamily: "DM Mono, monospace" }}>{label}</p>
      <div className="relative overflow-hidden rounded-[42px] bg-[#0d1117] shadow-[0_40px_100px_rgba(0,0,0,0.7)]"
           style={{ width: 305, height: 612, border: "2px solid rgba(255,255,255,0.1)" }}>
        {/* Status bar */}
        <div className="relative z-50 flex items-center justify-between px-7 pt-3 pb-2">
          <span className="text-[11px] font-semibold text-white/70" style={{ fontFamily: "DM Mono, monospace" }}>9:41</span>
          <div className="flex items-center gap-1 text-white/60">
            <Signal size={11} /><Wifi size={11} /><Battery size={11} />
          </div>
        </div>
        {/* Screen content */}
        <div className="absolute inset-0 top-9 overflow-hidden">
          {children}
        </div>
      </div>
    </div>
  )
}

// ─── Resident App ─────────────────────────────────────────────────────────────

function ResidentHome() {
  const [cat, setCat] = useState("recyclable")
  const [vol, setVol] = useState(28)

  const cats = [
    { id: "recyclable", emoji: "♻️", label: "Recyclable" },
    { id: "hazardous",  emoji: "☢️", label: "Hazardous" },
    { id: "general",    emoji: "🗑️", label: "General" },
    { id: "ewaste",     emoji: "💻", label: "E-Waste" },
    { id: "organic",    emoji: "🌱", label: "Organic" },
  ]
  const volLabel = vol < 18 ? "1 Bag" : vol < 40 ? `${Math.round(vol / 8)} Bags` : vol < 70 ? "Half Truck" : "Full Truck"

  return (
    <div className="relative h-full">
      <MapBg />

      {/* User pin */}
      <div className="absolute z-10" style={{ top: "35%", left: "44%" }}>
        <div className="w-4 h-4 rounded-full bg-emerald-400 border-2 border-white shadow-[0_0_12px_rgba(34,197,94,0.8)]">
          <div className="absolute inset-0 rounded-full bg-emerald-400 animate-ping opacity-50" />
        </div>
        <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-0 h-0"
             style={{ borderLeft: "5px solid transparent", borderRight: "5px solid transparent", borderTop: "7px solid white" }} />
      </div>

      {/* Search bar */}
      <div className="absolute top-2 left-3 right-3 z-20">
        <div className={`flex items-center gap-2.5 px-4 py-3 rounded-2xl ${glass()}`}>
          <Search size={14} className="text-white/35 flex-shrink-0" />
          <span className="text-sm text-white/35 flex-1">Where&apos;s the waste located?</span>
          <div className="w-7 h-7 rounded-xl bg-emerald-500/20 flex items-center justify-center">
            <MapPin size={13} className="text-emerald-400" />
          </div>
        </div>
      </div>

      {/* Bottom sheet */}
      <div className={`absolute bottom-0 left-0 right-0 z-20 rounded-t-[28px] px-5 pt-3 pb-5 ${glass()}`}
           style={{ borderTop: "1px solid rgba(255,255,255,0.1)" }}>
        <div className="w-9 h-1 rounded-full bg-white/15 mx-auto mb-4" />

        {/* Category */}
        <p className="text-[9px] font-bold text-white/30 uppercase tracking-[0.18em] mb-2.5">Waste Type</p>
        <div className="flex gap-2 overflow-x-auto pb-1 mb-4" style={{ scrollbarWidth: "none" }}>
          {cats.map(c => (
            <button key={c.id} onClick={() => setCat(c.id)}
                    className="flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all duration-200"
                    style={cat === c.id
                      ? { background: "#22c55e", color: "#0a0d12", boxShadow: "0 4px 20px rgba(34,197,94,0.4)" }
                      : { background: "rgba(255,255,255,0.06)", color: "rgba(255,255,255,0.55)" }}>
              <span>{c.emoji}</span><span>{c.label}</span>
            </button>
          ))}
        </div>

        {/* Volume slider */}
        <div className="mb-4">
          <div className="flex justify-between items-center mb-2">
            <p className="text-[9px] font-bold text-white/30 uppercase tracking-[0.18em]">Volume</p>
            <span className="text-xs font-bold text-emerald-400" style={{ fontFamily: "DM Mono, monospace" }}>{volLabel}</span>
          </div>
          <input type="range" min={0} max={100} value={vol} onChange={e => setVol(+e.target.value)}
                 className="w-full h-1.5 rounded-full outline-none cursor-pointer appearance-none"
                 style={{ background: `linear-gradient(to right, #22c55e ${vol}%, rgba(255,255,255,0.1) ${vol}%)` }} />
          <div className="flex justify-between mt-1">
            <span className="text-[9px] text-white/25">1 Bag</span>
            <span className="text-[9px] text-white/25">Full Truck</span>
          </div>
        </div>

        {/* Gate photo */}
        <div className="flex items-center gap-3 p-3 rounded-2xl mb-4"
             style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.06)" }}>
          <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
               style={{ background: "rgba(34,197,94,0.12)", border: "1px solid rgba(34,197,94,0.2)" }}>
            <Camera size={18} className="text-emerald-400" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-white">Find My Gate</p>
            <p className="text-[10px] text-white/35 mt-0.5">Snap gate or landmark for driver</p>
          </div>
          <ChevronRight size={14} className="text-white/25 flex-shrink-0" />
        </div>

        {/* CTA */}
        <button className="w-full py-4 rounded-2xl text-sm font-bold text-[#0a0d12] tracking-wide transition-transform active:scale-[0.98]"
                style={{ background: "linear-gradient(135deg, #22c55e, #16a34a)", boxShadow: "0 10px 36px rgba(34,197,94,0.45)" }}>
          Confirm Pickup
        </button>
      </div>
    </div>
  )
}

function ResidentTracking() {
  return (
    <div className="relative h-full">
      <MapBg variant="nav" />

      {/* Truck icon */}
      <div className="absolute z-10" style={{ top: "28%", left: "42%" }}>
        <div className="relative">
          <div className="w-11 h-11 rounded-2xl flex items-center justify-center shadow-[0_0_24px_rgba(34,197,94,0.6)]"
               style={{ background: "#22c55e" }}>
            <Truck size={19} className="text-white" />
          </div>
          <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-emerald-300 animate-ping" />
        </div>
      </div>

      {/* User pin */}
      <div className="absolute z-10" style={{ top: "68%", left: "44%" }}>
        <div className="w-4 h-4 rounded-full bg-white border-2 border-emerald-400 shadow-lg">
          <div className="absolute inset-0 rounded-full bg-white animate-ping opacity-40" />
        </div>
      </div>

      {/* ETA pill */}
      <div className="absolute top-3 left-1/2 -translate-x-1/2 z-20">
        <div className="flex items-center gap-2 px-4 py-2 rounded-full"
             style={{ background: "rgba(34,197,94,0.15)", backdropFilter: "blur(14px)", border: "1px solid rgba(34,197,94,0.3)" }}>
          <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span className="text-sm font-bold text-emerald-400">Arriving in 4 mins</span>
        </div>
      </div>

      {/* FABs */}
      <div className="absolute right-3 z-20 flex flex-col gap-2.5" style={{ top: "38%" }}>
        {[{ icon: Phone, color: "text-white" }, { icon: MessageCircle, color: "text-emerald-400" }].map(({ icon: Icon, color }, i) => (
          <button key={i} className={`w-11 h-11 rounded-2xl flex items-center justify-center ${color} ${glass()}`}>
            <Icon size={17} />
          </button>
        ))}
      </div>

      {/* Driver bottom card */}
      <div className={`absolute bottom-0 left-0 right-0 z-20 rounded-t-[28px] px-5 pt-3 pb-5 ${glass()}`}
           style={{ borderTop: "1px solid rgba(255,255,255,0.1)" }}>
        <div className="w-9 h-1 rounded-full bg-white/15 mx-auto mb-4" />

        <div className="flex items-center gap-3.5 mb-4">
          <div className="w-14 h-14 rounded-2xl overflow-hidden flex-shrink-0 bg-emerald-500/10">
            <img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=80&h=80&fit=crop&auto=format"
                 alt="Driver Emeka Okafor" className="w-full h-full object-cover" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-white">Emeka Okafor</p>
            <div className="flex items-center gap-1.5 mt-0.5">
              <Star size={11} className="text-yellow-400 fill-yellow-400" />
              <span className="text-xs font-semibold text-white/70">4.9</span>
              <span className="text-white/25 text-xs">·</span>
              <span className="text-xs text-white/35">Senior Collector</span>
            </div>
          </div>
          <span className="text-xs font-bold px-2 py-1 rounded-xl text-white/50"
                style={{ fontFamily: "DM Mono, monospace", background: "rgba(255,255,255,0.06)" }}>
            LG-401-KJA
          </span>
        </div>

        {/* Progress */}
        <div className="h-1 rounded-full overflow-hidden mb-1" style={{ background: "rgba(255,255,255,0.07)" }}>
          <div className="h-full rounded-full bg-emerald-400 transition-all" style={{ width: "65%" }} />
        </div>
        <p className="text-[10px] text-white/25 text-center mb-4">En route · 2.4 km remaining</p>

        {/* Actions */}
        <div className="flex gap-2.5">
          <button className="flex-1 flex items-center justify-center gap-1.5 py-3 rounded-2xl text-xs font-bold text-white"
                  style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.08)" }}>
            <Phone size={14} /> Call Driver
          </button>
          <button className="flex-1 flex items-center justify-center gap-1.5 py-3 rounded-2xl text-xs font-bold text-emerald-400"
                  style={{ background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.2)" }}>
            <MessageCircle size={14} /> WhatsApp
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Collector App ────────────────────────────────────────────────────────────

function CollectorDashboard() {
  const [online, setOnline] = useState(true)
  const [dismissed, setDismissed] = useState(false)

  return (
    <div className="relative h-full">
      <MapBg variant="radar" />

      {/* Ping blips */}
      {([[28,38],[68,47],[22,62]] as [number,number][]).map(([l,t],i) => (
        <div key={i} className="absolute z-10 w-3 h-3 rounded-full bg-yellow-400"
             style={{ left: `${l}%`, top: `${t}%`, boxShadow: "0 0 8px rgba(250,204,21,0.7)", animationDelay: `${i * 0.4}s` }}>
          <div className="absolute inset-0 rounded-full bg-yellow-400 animate-ping opacity-60"
               style={{ animationDelay: `${i * 0.4}s` }} />
        </div>
      ))}

      {/* Online toggle */}
      <div className={`absolute top-2 left-3 right-3 z-20 flex items-center justify-between px-4 py-3 rounded-2xl ${glass()}`}>
        <div>
          <p className="text-[9px] text-white/30 uppercase tracking-widest font-bold">Status</p>
          <p className={`text-sm font-bold mt-0.5 ${online ? "text-emerald-400" : "text-white/35"}`}>
            {online ? "Online & Active" : "Offline"}
          </p>
        </div>
        <button onClick={() => setOnline(!online)}
                className="relative w-14 h-7 rounded-full transition-all duration-300"
                style={online
                  ? { background: "#22c55e", boxShadow: "0 0 16px rgba(34,197,94,0.5)" }
                  : { background: "rgba(255,255,255,0.1)" }}>
          <div className={`absolute top-0.5 w-6 h-6 rounded-full bg-white shadow-md transition-all duration-300 ${online ? "right-0.5" : "left-0.5"}`} />
        </button>
      </div>

      {/* Today earnings */}
      <div className="absolute top-20 right-3 z-20">
        <div className="px-3 py-2 rounded-2xl text-center"
             style={{ background: "rgba(13,18,28,0.88)", backdropFilter: "blur(12px)", border: "1px solid rgba(34,197,94,0.2)" }}>
          <p className="text-[9px] text-white/35">Today</p>
          <p className="text-sm font-bold text-emerald-400" style={{ fontFamily: "DM Mono, monospace" }}>₦34,200</p>
        </div>
      </div>

      {/* Incoming request card */}
      {!dismissed && (
        <div className="absolute z-20 left-3 right-3 p-4 rounded-3xl"
             style={{
               top: "38%",
               background: "rgba(11,18,30,0.97)", backdropFilter: "blur(20px)",
               border: "1px solid rgba(34,197,94,0.25)",
               boxShadow: "0 24px 64px rgba(0,0,0,0.7)",
             }}>
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-1.5">
              <div className="w-1.5 h-1.5 rounded-full bg-yellow-400 animate-pulse" />
              <span className="text-[9px] font-bold text-yellow-400 uppercase tracking-widest">New Request</span>
            </div>
            <span className="text-[9px] text-white/25" style={{ fontFamily: "DM Mono, monospace" }}>REQ-4821</span>
          </div>

          <div className="grid grid-cols-3 gap-2 mb-3.5">
            {[
              { label: "Distance", value: "2.1 km" },
              { label: "Volume",   value: "3 Bags" },
              { label: "Payout",   value: "₦2,400", green: true },
            ].map(({ label, value, green }) => (
              <div key={label} className="text-center p-2 rounded-xl"
                   style={green
                     ? { background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.15)" }
                     : { background: "rgba(255,255,255,0.04)" }}>
                <p className="text-[9px] text-white/35">{label}</p>
                <p className={`text-sm font-bold mt-0.5 ${green ? "text-emerald-400" : "text-white"}`}
                   style={{ fontFamily: "DM Mono, monospace" }}>{value}</p>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-1.5 mb-3.5">
            <MapPin size={11} className="text-white/30 flex-shrink-0" />
            <span className="text-[10px] text-white/50">14 Adeola Odeku Street, Victoria Island</span>
          </div>

          <div className="flex gap-2">
            <button onClick={() => setDismissed(true)}
                    className="flex-1 py-3 rounded-2xl text-xs font-bold text-white/50 transition-colors hover:bg-white/5"
                    style={{ border: "1px solid rgba(255,255,255,0.1)" }}>
              Decline
            </button>
            <button className="flex-1 py-3 rounded-2xl text-xs font-bold text-[#0a0d12] transition-all active:scale-[0.98]"
                    style={{ background: "linear-gradient(135deg, #22c55e, #16a34a)", boxShadow: "0 8px 24px rgba(34,197,94,0.4)" }}>
              Accept
            </button>
          </div>
        </div>
      )}

      {/* Stats bar */}
      <div className="absolute bottom-0 left-0 right-0 z-20 px-4 py-3.5"
           style={{ background: "rgba(10,13,18,0.95)", borderTop: "1px solid rgba(255,255,255,0.06)" }}>
        <div className="flex justify-around">
          {[
            { label: "Jobs Today", value: "7" },
            { label: "On-Clock",   value: "3h 24m" },
            { label: "Rating",     value: "4.9 ⭐" },
          ].map(({ label, value }) => (
            <div key={label} className="text-center">
              <p className="text-[9px] text-white/25 uppercase tracking-widest">{label}</p>
              <p className="text-xs font-bold text-white mt-0.5" style={{ fontFamily: "DM Mono, monospace" }}>{value}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function CollectorMission() {
  const [verified, setVerified] = useState(false)
  const [snapping, setSnapping] = useState(false)

  return (
    <div className="relative h-full">
      <MapBg variant="nav" />

      {/* Truck */}
      <div className="absolute z-10 w-11 h-11 rounded-2xl flex items-center justify-center"
           style={{ top: "42%", left: "41%", background: "#22c55e", boxShadow: "0 0 24px rgba(34,197,94,0.6)" }}>
        <Truck size={19} className="text-white" />
      </div>

      {/* Destination pin */}
      <div className="absolute z-10 w-6 h-6 rounded-full bg-white flex items-center justify-center shadow-xl"
           style={{ top: "19%", left: "40%" }}>
        <MapPin size={12} className="text-emerald-600" />
      </div>

      {/* Turn banner */}
      <div className={`absolute top-2 left-3 right-3 z-20 flex items-center gap-3 px-4 py-3 rounded-2xl ${glass()}`}
           style={{ border: "1px solid rgba(34,197,94,0.2)" }}>
        <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
             style={{ background: "rgba(34,197,94,0.15)" }}>
          <ChevronRight size={18} className="text-emerald-400" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-[9px] text-white/30">In 180 m — Turn Right</p>
          <p className="text-sm font-bold text-white">Adeola Odeku St</p>
        </div>
        <div className="text-right flex-shrink-0">
          <p className="text-[9px] text-white/30">ETA</p>
          <p className="text-xs font-bold text-emerald-400" style={{ fontFamily: "DM Mono, monospace" }}>2 min</p>
        </div>
      </div>

      {/* Mission bottom sheet */}
      <div className={`absolute bottom-0 left-0 right-0 z-20 rounded-t-[28px] px-5 pt-3 pb-4 ${glass()}`}
           style={{ borderTop: "1px solid rgba(255,255,255,0.1)" }}>
        <div className="w-9 h-1 rounded-full bg-white/15 mx-auto mb-4" />

        {verified ? (
          <div className="flex flex-col items-center py-6">
            <div className="w-16 h-16 rounded-full flex items-center justify-center mb-3"
                 style={{ background: "rgba(34,197,94,0.15)" }}>
              <CheckCircle size={32} className="text-emerald-400" />
            </div>
            <p className="text-base font-bold text-white">Job Complete!</p>
            <p className="text-xs text-white/40 mt-1">₦2,400 added to your wallet</p>
          </div>
        ) : (
          <>
            {/* Client row */}
            <div className="flex items-center gap-3 mb-3.5">
              <div className="flex-1 min-w-0">
                <p className="text-[9px] text-white/30 uppercase tracking-widest mb-0.5">Destination</p>
                <p className="text-xs font-bold text-white">14 Adeola Odeku St, VI</p>
                <p className="text-[9px] text-white/30 mt-0.5">♻️ Recyclable · 3 Bags · ₦2,400</p>
              </div>
              {/* Gate thumbnail */}
              <div className="relative w-14 h-14 rounded-2xl overflow-hidden flex-shrink-0"
                   style={{ border: "1px solid rgba(255,255,255,0.1)" }}>
                <img src="https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=80&h=80&fit=crop&auto=format"
                     alt="Gate landmark" className="w-full h-full object-cover opacity-60" />
                <div className="absolute bottom-0 inset-x-0 py-0.5 text-center text-[8px] text-white/70"
                     style={{ background: "rgba(0,0,0,0.5)" }}>Gate</div>
              </div>
            </div>

            {/* Verify button */}
            {!snapping ? (
              <button onClick={() => setSnapping(true)}
                      className="w-full py-3 rounded-2xl text-xs font-bold text-white mb-3 flex items-center justify-center gap-2 transition-all hover:bg-white/8"
                      style={{ background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.1)" }}>
                <Camera size={14} /> Verify Collection
              </button>
            ) : (
              <div className="w-full py-3 rounded-2xl text-xs font-bold text-center text-emerald-400 mb-3"
                   style={{ background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.2)" }}>
                📸 Snap Proof of Collection
              </div>
            )}

            {/* Swipe to finish */}
            <div className="relative h-13 rounded-2xl overflow-hidden select-none"
                 style={{ height: 52, background: "rgba(34,197,94,0.07)", border: "1px solid rgba(34,197,94,0.15)" }}>
              <div className="absolute inset-0 flex items-center justify-center">
                <span className="text-xs font-bold text-emerald-400/50">Swipe to finish job →</span>
              </div>
              <button onClick={() => setVerified(true)}
                      className="absolute left-1.5 top-1.5 bottom-1.5 w-10 rounded-xl flex items-center justify-center transition-all active:scale-95"
                      style={{ background: "#22c55e", boxShadow: "0 4px 16px rgba(34,197,94,0.5)" }}>
                <ChevronRight size={16} className="text-white" />
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ─── Admin Dashboard ──────────────────────────────────────────────────────────

function AdminDashboard() {
  const [navTab, setNavTab] = useState("overview")
  const [selected, setSelected] = useState<string | null>(null)

  const stats = [
    { label: "Trucks Active",   value: "12", unit: "/ 18 fleet", icon: Truck,     color: "#22c55e", bg: "rgba(34,197,94,0.1)" },
    { label: "Avg Collection",  value: "24", unit: "min",         icon: Clock,     color: "#60a5fa", bg: "rgba(96,165,250,0.1)" },
    { label: "Tons Today",      value: "38.4",unit: "t",          icon: Package,   color: "#f59e0b", bg: "rgba(245,158,11,0.1)" },
    { label: "Active Residents",value: "1,284",unit: "users",     icon: Users,     color: "#a78bfa", bg: "rgba(167,139,250,0.1)" },
  ]

  const navItems = [
    { id: "overview",  icon: Activity  },
    { id: "fleet",     icon: Truck     },
    { id: "map",       icon: Map       },
    { id: "analytics", icon: BarChart2 },
    { id: "users",     icon: Users     },
    { id: "settings",  icon: Settings  },
  ]

  return (
    <div className="flex h-full overflow-hidden" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>

      {/* Icon sidebar */}
      <div className="w-[60px] flex flex-col items-center py-5 gap-3 flex-shrink-0"
           style={{ background: "#080b0f", borderRight: "1px solid rgba(255,255,255,0.05)" }}>
        <div className="w-9 h-9 rounded-xl flex items-center justify-center mb-3 flex-shrink-0"
             style={{ background: "#22c55e", boxShadow: "0 4px 20px rgba(34,197,94,0.45)" }}>
          <Truck size={16} className="text-[#0a0d12]" />
        </div>
        {navItems.map(({ id, icon: Icon }) => (
          <button key={id} onClick={() => setNavTab(id)}
                  className="w-10 h-10 rounded-xl flex items-center justify-center transition-all duration-150"
                  style={navTab === id
                    ? { background: "rgba(34,197,94,0.15)", color: "#22c55e" }
                    : { color: "rgba(255,255,255,0.22)" }}>
            <Icon size={17} />
          </button>
        ))}
        <div className="mt-auto">
          <button className="w-10 h-10 rounded-xl flex items-center justify-center"
                  style={{ color: "rgba(255,255,255,0.18)" }}>
            <LogOut size={15} />
          </button>
        </div>
      </div>

      {/* Live feed panel */}
      <div className="w-[268px] flex flex-col flex-shrink-0"
           style={{ background: "#0c1018", borderRight: "1px solid rgba(255,255,255,0.05)" }}>
        {/* Header */}
        <div className="px-4 py-4 flex items-center justify-between flex-shrink-0"
             style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
          <div>
            <p className="text-[10px] font-bold text-white uppercase tracking-[0.18em]">Live Feed</p>
            <p className="text-[10px] text-white/25 mt-0.5">Real-time requests</p>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-[10px] font-bold text-emerald-400" style={{ fontFamily: "DM Mono, monospace" }}>LIVE</span>
          </div>
        </div>

        {/* Ticker list */}
        <div className="flex-1 overflow-y-auto" style={{ scrollbarWidth: "none" }}>
          {liveRequests.map((r, i) => (
            <div key={r.id} className="px-4 py-3 cursor-pointer transition-colors hover:bg-white/[0.02]"
                 style={{ borderBottom: "1px solid rgba(255,255,255,0.03)" }}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-[9px] text-white/25" style={{ fontFamily: "DM Mono, monospace" }}>{r.id}</span>
                <StatusPill status={r.status} />
              </div>
              <p className="text-[11px] font-semibold text-white/75 truncate mb-1">{r.location}</p>
              <div className="flex items-center justify-between">
                <span className="text-[9px] text-white/25">{r.volume}</span>
                <span className="text-[10px] font-bold text-emerald-400" style={{ fontFamily: "DM Mono, monospace" }}>{r.payout}</span>
              </div>
              <p className="text-[9px] text-white/18 mt-0.5">{r.time}</p>
            </div>
          ))}
        </div>

        {/* Fleet mini list */}
        <div className="px-4 py-3 flex-shrink-0" style={{ borderTop: "1px solid rgba(255,255,255,0.06)" }}>
          <p className="text-[9px] text-white/25 uppercase tracking-widest mb-2">Fleet</p>
          <div className="space-y-2">
            {fleet.slice(0, 4).map(t => (
              <div key={t.id} onClick={() => setSelected(selected === t.id ? null : t.id)}
                   className="flex items-center gap-2 cursor-pointer rounded-lg px-1 py-1 transition-colors hover:bg-white/[0.03]">
                <div className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0"
                     style={{ background: t.status === "en-route" ? "rgba(34,197,94,0.15)" : "rgba(255,255,255,0.05)" }}>
                  <Truck size={11} style={{ color: t.status === "en-route" ? "#22c55e" : "rgba(255,255,255,0.25)" }} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[10px] font-semibold text-white/60 truncate">{t.driver}</p>
                  <p className="text-[9px] text-white/25">{t.zone}</p>
                </div>
                <div className="flex-shrink-0 text-right">
                  <div className="w-10 h-1 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.07)" }}>
                    <div className="h-full rounded-full bg-emerald-400 transition-all" style={{ width: `${t.load}%` }} />
                  </div>
                  <p className="text-[9px] text-white/25 mt-0.5">{t.load}%</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* Topbar */}
        <div className="flex items-center justify-between px-6 py-3.5 flex-shrink-0"
             style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
          <div>
            <h1 className="text-base font-bold text-white">Command Center</h1>
            <p className="text-[10px] text-white/25 mt-0.5">Lagos Metropolitan Zone · Mon 30 Jun 2026</p>
          </div>
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[11px] text-white/40 transition-colors hover:text-white/60"
                    style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.06)" }}>
              <RefreshCw size={11} /> Refresh
            </button>
            <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl"
                 style={{ background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.2)" }}>
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-[10px] font-bold text-emerald-400">Live</span>
            </div>
            <Bell size={15} className="text-white/25" />
            <div className="w-8 h-8 rounded-xl overflow-hidden">
              <img src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=48&h=48&fit=crop&auto=format"
                   alt="Admin user" className="w-full h-full object-cover" />
            </div>
          </div>
        </div>

        {/* Stat cards */}
        <div className="grid grid-cols-4 gap-3 px-6 py-4 flex-shrink-0">
          {stats.map(s => (
            <div key={s.label} className="p-4 rounded-2xl"
                 style={{ background: "rgba(255,255,255,0.025)", border: "1px solid rgba(255,255,255,0.06)" }}>
              <div className="flex items-start justify-between mb-3">
                <p className="text-[10px] font-bold text-white/30 uppercase tracking-widest leading-tight">{s.label}</p>
                <div className="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0"
                     style={{ background: s.bg }}>
                  <s.icon size={14} style={{ color: s.color }} />
                </div>
              </div>
              <div className="flex items-baseline gap-1.5">
                <span className="text-[22px] font-bold text-white leading-none" style={{ fontFamily: "DM Mono, monospace" }}>{s.value}</span>
                <span className="text-xs text-white/25">{s.unit}</span>
              </div>
              <div className="flex items-center gap-1 mt-2">
                <TrendingUp size={9} className="text-emerald-400" />
                <span className="text-[9px] text-emerald-400">+12% vs yesterday</span>
              </div>
            </div>
          ))}
        </div>

        {/* Map + charts */}
        <div className="flex gap-4 px-6 pb-6 flex-1 overflow-hidden min-h-0">
          {/* City map */}
          <div className="flex-1 rounded-2xl overflow-hidden relative"
               style={{ background: "#0c1520", border: "1px solid rgba(255,255,255,0.06)" }}>
            <svg className="absolute inset-0 w-full h-full" viewBox="0 0 580 340" preserveAspectRatio="xMidYMid slice">
              <defs>
                <pattern id="ag" width="28" height="28" patternUnits="userSpaceOnUse">
                  <path d="M28 0L0 0 0 28" fill="none" stroke="rgba(255,255,255,0.04)" strokeWidth="0.4" />
                </pattern>
                <radialGradient id="h1" cx="38%" cy="32%" r="16%">
                  <stop offset="0%" stopColor="#22c55e" stopOpacity="0.28" />
                  <stop offset="100%" stopColor="#22c55e" stopOpacity="0" />
                </radialGradient>
                <radialGradient id="h2" cx="64%" cy="60%" r="18%">
                  <stop offset="0%" stopColor="#f59e0b" stopOpacity="0.22" />
                  <stop offset="100%" stopColor="#f59e0b" stopOpacity="0" />
                </radialGradient>
                <radialGradient id="h3" cx="22%" cy="72%" r="12%">
                  <stop offset="0%" stopColor="#22c55e" stopOpacity="0.18" />
                  <stop offset="100%" stopColor="#22c55e" stopOpacity="0" />
                </radialGradient>
                <filter id="aglow">
                  <feGaussianBlur stdDeviation="2.5" result="b" />
                  <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
                </filter>
              </defs>
              <rect width="580" height="340" fill="url(#ag)" />

              {/* Roads */}
              <path d="M0,175 Q145,162 290,175 Q435,188 580,175" stroke="rgba(255,255,255,0.09)" strokeWidth="13" fill="none" />
              <path d="M0,105 Q190,95 290,108 Q430,122 580,108"  stroke="rgba(255,255,255,0.06)" strokeWidth="7"  fill="none" />
              <path d="M0,265 Q190,255 290,268 Q430,282 580,268" stroke="rgba(255,255,255,0.06)" strokeWidth="7"  fill="none" />
              <path d="M145,0 Q152,85 145,175 Q138,265 145,340"  stroke="rgba(255,255,255,0.07)" strokeWidth="9"  fill="none" />
              <path d="M370,0 Q378,85 370,175 Q362,265 370,340"  stroke="rgba(255,255,255,0.07)" strokeWidth="9"  fill="none" />
              <path d="M55,0 Q60,85 55,340"   stroke="rgba(255,255,255,0.04)" strokeWidth="4" fill="none" />
              <path d="M500,0 Q506,85 500,340" stroke="rgba(255,255,255,0.04)" strokeWidth="4" fill="none" />

              {/* City blocks */}
              {([
                [8,8,120,80],[160,8,190,80],[385,8,105,80],[500,8,70,80],
                [8,130,120,30],[160,130,190,30],[385,130,105,30],[500,130,70,30],
                [8,195,120,55],[160,195,190,55],[385,195,105,55],[500,195,70,55],
                [8,285,120,48],[160,285,190,48],[385,285,105,48],[500,285,70,48],
              ] as [number,number,number,number][]).map(([x,y,w,h],i) => (
                <rect key={i} x={x} y={y} width={w} height={h} rx="3" fill="rgba(255,255,255,0.022)" />
              ))}

              {/* Heatmaps */}
              <rect width="580" height="340" fill="url(#h1)" />
              <rect width="580" height="340" fill="url(#h2)" />
              <rect width="580" height="340" fill="url(#h3)" />

              {/* Animated truck dots */}
              {([
                { x: 220, y: 162, dx: [220,232,226,220], dy: [162,157,152,162], dur: "4s" },
                { x: 375, y: 188, dx: [375,381,387,375], dy: [188,188,188,188], dur: "5.5s" },
                { x: 140, y: 255, dx: [140,140,140,140], dy: [255,245,250,255], dur: "3.8s" },
              ]).map((t,i) => (
                <g key={i}>
                  <circle cx={t.x} cy={t.y} r="5" fill="#22c55e" filter="url(#aglow)">
                    <animate attributeName="cx" values={t.dx.join(";")} dur={t.dur} repeatCount="indefinite" />
                    <animate attributeName="cy" values={t.dy.join(";")} dur={t.dur} repeatCount="indefinite" />
                  </circle>
                  <circle cx={t.x} cy={t.y} r="10" fill="none" stroke="#22c55e" strokeWidth="1" opacity="0.35">
                    <animate attributeName="r"       values="8;18;8"     dur="2.2s" repeatCount="indefinite" begin={`${i*0.7}s`}/>
                    <animate attributeName="opacity" values="0.35;0;0.35" dur="2.2s" repeatCount="indefinite" begin={`${i*0.7}s`}/>
                    <animate attributeName="cx" values={t.dx.join(";")} dur={t.dur} repeatCount="indefinite" />
                    <animate attributeName="cy" values={t.dy.join(";")} dur={t.dur} repeatCount="indefinite" />
                  </circle>
                </g>
              ))}

              {/* Pending request markers */}
              {([
                [175, 92], [420, 60], [310, 262], [76, 232], [465, 295],
              ] as [number,number][]).map(([x,y], i) => (
                <g key={i}>
                  <rect x={x-8} y={y-8} width={16} height={16} rx="4" fill="rgba(245,158,11,0.85)" />
                  <text x={x} y={y+4} textAnchor="middle" fontSize="10" fill="white" fontWeight="bold">!</text>
                </g>
              ))}

              {/* Dumpsite markers */}
              {([[90,50],[480,135]] as [number,number][]).map(([x,y],i) => (
                <g key={i}>
                  <circle cx={x} cy={y} r="7" fill="rgba(96,165,250,0.7)" />
                  <text x={x} y={y+3} textAnchor="middle" fontSize="8" fill="white" fontWeight="bold">D</text>
                </g>
              ))}
            </svg>

            {/* Map labels */}
            <div className="absolute top-3 left-3 flex items-center gap-2">
              <span className="text-[10px] font-semibold text-white/50 px-3 py-1.5 rounded-lg"
                    style={{ background: "rgba(8,11,15,0.85)", border: "1px solid rgba(255,255,255,0.07)" }}>
                Lagos Metro Area
              </span>
            </div>

            {/* Legend */}
            <div className="absolute bottom-3 left-3 px-3 py-2.5 rounded-xl"
                 style={{ background: "rgba(8,11,15,0.88)", border: "1px solid rgba(255,255,255,0.07)" }}>
              {[
                { color: "#22c55e", label: "Active Truck" },
                { color: "#f59e0b", label: "Pending Request" },
                { color: "#60a5fa", label: "Dumpsite" },
              ].map(({ color, label }) => (
                <div key={label} className="flex items-center gap-2 mb-1 last:mb-0">
                  <div className="w-2 h-2 rounded-full" style={{ background: color }} />
                  <span className="text-[9px] text-white/40">{label}</span>
                </div>
              ))}
            </div>

            {/* Truck detail panel */}
            {selected && (() => {
              const t = fleet.find(f => f.id === selected)
              if (!t) return null
              return (
                <div className="absolute top-3 right-3 w-44 p-3.5 rounded-2xl"
                     style={{ background: "rgba(8,11,15,0.94)", backdropFilter: "blur(14px)", border: "1px solid rgba(34,197,94,0.25)" }}>
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-bold text-white" style={{ fontFamily: "DM Mono, monospace" }}>{t.id}</p>
                    <button onClick={() => setSelected(null)} className="text-white/30 hover:text-white/60 text-base leading-none">×</button>
                  </div>
                  <p className="text-[11px] font-semibold text-white/60">{t.driver}</p>
                  <p className="text-[10px] text-white/25 mb-2.5">{t.zone}</p>
                  <StatusPill status={t.status} />
                  <div className="mt-2.5 h-1 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.07)" }}>
                    <div className="h-full rounded-full bg-emerald-400" style={{ width: `${t.load}%` }} />
                  </div>
                  <p className="text-[9px] text-white/25 mt-1">Payload: {t.load}%</p>
                </div>
              )
            })()}
          </div>

          {/* Charts panel */}
          <div className="w-[220px] flex flex-col gap-4 flex-shrink-0">
            {/* Weekly tons */}
            <div className="flex-1 p-4 rounded-2xl"
                 style={{ background: "rgba(255,255,255,0.025)", border: "1px solid rgba(255,255,255,0.06)" }}>
              <p className="text-[10px] font-bold text-white/30 uppercase tracking-widest mb-3">Weekly Tonnage</p>
              <ResponsiveContainer width="100%" height={90}>
                <AreaChart data={weeklyTons} margin={{ top: 2, right: 2, bottom: 0, left: -22 }}>
                  <defs>
                    <linearGradient id="gGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#22c55e" stopOpacity="0.35" />
                      <stop offset="100%" stopColor="#22c55e" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="day" tick={{ fontSize: 8, fill: "rgba(255,255,255,0.28)" }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 8, fill: "rgba(255,255,255,0.28)" }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ background: "#111720", border: "1px solid rgba(255,255,255,0.1)", borderRadius: 10, fontSize: 11, color: "#fff" }}
                    cursor={{ stroke: "rgba(34,197,94,0.3)" }}
                  />
                  <Area type="monotone" dataKey="tons" stroke="#22c55e" strokeWidth={2} fill="url(#gGrad)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            {/* Hourly requests */}
            <div className="flex-1 p-4 rounded-2xl"
                 style={{ background: "rgba(255,255,255,0.025)", border: "1px solid rgba(255,255,255,0.06)" }}>
              <p className="text-[10px] font-bold text-white/30 uppercase tracking-widest mb-3">Requests / Hour</p>
              <ResponsiveContainer width="100%" height={90}>
                <BarChart data={hourlyRequests} margin={{ top: 2, right: 2, bottom: 0, left: -22 }}>
                  <XAxis dataKey="hour" tick={{ fontSize: 8, fill: "rgba(255,255,255,0.28)" }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 8, fill: "rgba(255,255,255,0.28)" }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ background: "#111720", border: "1px solid rgba(255,255,255,0.1)", borderRadius: 10, fontSize: 11, color: "#fff" }}
                    cursor={{ fill: "rgba(255,255,255,0.04)" }}
                  />
                  <Bar dataKey="n" fill="#22c55e" opacity={0.75} radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* Fleet summary */}
            <div className="p-4 rounded-2xl flex-shrink-0"
                 style={{ background: "rgba(255,255,255,0.025)", border: "1px solid rgba(255,255,255,0.06)" }}>
              <p className="text-[10px] font-bold text-white/30 uppercase tracking-widest mb-3">Fleet Load</p>
              <div className="space-y-2">
                {fleet.map(t => (
                  <div key={t.id} className="flex items-center gap-2 cursor-pointer" onClick={() => setSelected(t.id === selected ? null : t.id)}>
                    <span className="text-[9px] text-white/30 w-12 flex-shrink-0" style={{ fontFamily: "DM Mono, monospace" }}>{t.id}</span>
                    <div className="flex-1 h-1 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.07)" }}>
                      <div className="h-full rounded-full transition-all"
                           style={{ width: `${t.load}%`, background: t.load > 70 ? "#f59e0b" : "#22c55e" }} />
                    </div>
                    <span className="text-[9px] text-white/25 w-6 text-right flex-shrink-0" style={{ fontFamily: "DM Mono, monospace" }}>{t.load}%</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Root App ─────────────────────────────────────────────────────────────────

type AppView = "resident" | "collector" | "admin"

export default function App() {
  const [view, setView] = useState<AppView>("admin")

  const tabs: { id: AppView; label: string }[] = [
    { id: "resident",  label: "Resident App" },
    { id: "collector", label: "Collector App" },
    { id: "admin",     label: "Admin Dashboard" },
  ]

  return (
    <div className="min-h-screen flex flex-col overflow-hidden"
         style={{ background: "#0a0d12", fontFamily: "'Plus Jakarta Sans', sans-serif" }}>

      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 flex-shrink-0 z-50"
              style={{ background: "rgba(8,11,15,0.9)", backdropFilter: "blur(16px)", borderBottom: "1px solid rgba(255,255,255,0.06)" }}>
        {/* Logo */}
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl flex items-center justify-center"
               style={{ background: "#22c55e", boxShadow: "0 4px 18px rgba(34,197,94,0.5)" }}>
            <Truck size={15} className="text-[#0a0d12]" />
          </div>
          <span className="text-[15px] font-bold text-white tracking-tight">
            Waste<span style={{ color: "#22c55e" }}>Link</span>
          </span>
        </div>

        {/* Tab switcher */}
        <div className="flex items-center gap-0.5 p-1 rounded-2xl"
             style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.07)" }}>
          {tabs.map(tab => (
            <button key={tab.id} onClick={() => setView(tab.id)}
                    className="px-4 py-1.5 rounded-xl text-sm font-semibold transition-all duration-200"
                    style={view === tab.id
                      ? { background: "#22c55e", color: "#0a0d12", boxShadow: "0 4px 16px rgba(34,197,94,0.4)" }
                      : { color: "rgba(255,255,255,0.35)" }}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* Right */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl"
               style={{ background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.2)" }}>
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-[10px] font-bold text-emerald-400">Live</span>
          </div>
          <Bell size={16} className="text-white/25" />
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 overflow-hidden">
        {view === "admin" && (
          <div className="h-full" style={{ height: "calc(100vh - 52px)" }}>
            <AdminDashboard />
          </div>
        )}

        {(view === "resident" || view === "collector") && (
          <div className="h-full flex items-center justify-center gap-12 py-8 overflow-auto"
               style={{ height: "calc(100vh - 52px)" }}>
            {view === "resident" && (
              <>
                <PhoneFrame label="Screen A — Home & Request">
                  <ResidentHome />
                </PhoneFrame>
                <PhoneFrame label="Screen B — Live Tracking">
                  <ResidentTracking />
                </PhoneFrame>
              </>
            )}
            {view === "collector" && (
              <>
                <PhoneFrame label="Screen A — Dashboard & Radar">
                  <CollectorDashboard />
                </PhoneFrame>
                <PhoneFrame label="Screen B — Active Mission">
                  <CollectorMission />
                </PhoneFrame>
              </>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
