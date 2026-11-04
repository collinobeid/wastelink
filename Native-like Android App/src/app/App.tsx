import { useState, useEffect } from "react";
import {
  Home,
  History,
  User,
  Trash2,
  Plus,
  MapPin,
  ChevronRight,
  ChevronLeft,
  Check,
  Clock,
  Truck,
  Package,
  X,
  Phone,
  MessageCircle,
  Bell,
  Globe,
  LogOut,
  Camera,
  Navigation,
  AlertCircle,
  BarChart3,
  Users,
  Settings,
  Shield,
  Zap,
  Layers,
  TreePine,
  Monitor,
  Archive,
  Filter,
  Search,
  MoreVertical,
  CheckCircle2,
  Circle,
  ArrowRight,
  Edit3,
  Download,
  TrendingUp,
  RefreshCw,
} from "lucide-react";
import { PieChart, Pie, Cell, LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, BarChart, Bar } from "recharts";

// ─── Types ──────────────────────────────────────────────────────────────────

type Locale = "en" | "sw";
type AppScreen =
  | "splash"
  | "login"
  | "register"
  | "home"
  | "new-request-category"
  | "new-request-volume"
  | "new-request-location"
  | "new-request-confirm"
  | "new-request-submitted"
  | "history"
  | "request-detail"
  | "profile"
  | "settings"
  | "admin-login"
  | "admin-map"
  | "admin-users"
  | "admin-analytics";

type Tab = "home" | "history" | "profile";
type AdminTab = "map" | "users" | "analytics";

type RequestStatus = "PENDING" | "ASSIGNED" | "IN_TRANSIT" | "COLLECTED" | "CLOSED" | "CANCELLED";
type WasteCategory = "HOUSEHOLD" | "CONSTRUCTION" | "GARDEN" | "ELECTRONIC" | "HAZARDOUS" | "BULK_ITEMS";
type VolumeTier = "SMALL_BAG" | "STANDARD_BIN" | "LARGE_LOAD" | "TRUCK_LOAD";

interface WasteRequest {
  id: string;
  requestRef: string;
  category: WasteCategory;
  volumeTier: VolumeTier;
  status: RequestStatus;
  address: string;
  notes?: string;
  createdAt: string;
  assignedUnit?: string;
  statusHistory: { status: RequestStatus; time: string; by: string }[];
}

// ─── i18n ────────────────────────────────────────────────────────────────────

const strings: Record<Locale, Record<string, string>> = {
  en: {
    appName: "WasteLink",
    tagline: "Smart Waste Collection",
    login: "Sign In",
    register: "Create Account",
    phoneLabel: "Phone Number",
    phonePlaceholder: "+255 712 345 678",
    passwordLabel: "Password",
    fullNameLabel: "Full Name",
    fullNamePlaceholder: "John Kamau",
    forgotPassword: "Forgot password?",
    noAccount: "Don't have an account?",
    hasAccount: "Already have an account?",
    navHome: "Home",
    navHistory: "History",
    navProfile: "Profile",
    greeting: "Good morning",
    homeSubtitle: "Request pickup or track your collection.",
    requestCTA: "Request Pickup",
    activeRequest: "Active Request",
    noActiveRequest: "No active requests",
    noActiveRequestSub: "Tap the button above to schedule a pickup.",
    selectCategory: "Select Waste Type",
    selectCategorySubtitle: "What kind of waste do you need collected?",
    selectVolume: "Estimated Volume",
    selectVolumeSubtitle: "Help us send the right vehicle.",
    confirmLocation: "Confirm Location",
    locationSubtitle: "Drag the pin to adjust your pickup point.",
    reviewRequest: "Review & Submit",
    reviewSubtitle: "Check the details before submitting.",
    submitRequest: "Submit Request",
    requestSubmitted: "Request Submitted!",
    requestSubmittedSub: "Your request has been logged and will be assigned shortly.",
    backToHome: "Back to Home",
    historyTitle: "My Requests",
    historyEmpty: "No requests yet",
    historyEmptySubtitle: "Your pickup history will appear here.",
    statusPending: "Pending",
    statusAssigned: "Assigned",
    statusInTransit: "On the Way",
    statusCollected: "Collected",
    statusClosed: "Closed",
    statusCancelled: "Cancelled",
    categoryHousehold: "Household",
    categoryConstruction: "Construction",
    categoryGarden: "Garden / Organic",
    categoryElectronic: "E-Waste",
    categoryHazardous: "Hazardous",
    categoryBulk: "Bulk / Large",
    volSmallBag: "Small Bag",
    volSmallBagDesc: "Less than 20 litres",
    volStandardBin: "Standard Bin",
    volStandardBinDesc: "20 – 80 litres",
    volLargeLoad: "Large Load",
    volLargeLoadDesc: "80 – 300 litres",
    volTruckLoad: "Truck Load",
    volTruckLoadDesc: "Over 300 litres",
    addressLabel: "Pickup Address",
    notesLabel: "Notes (optional)",
    notesPlaceholder: "Any extra details for the collection team…",
    contactSupport: "Contact Support via WhatsApp",
    timelineTitle: "Status Timeline",
    profileTitle: "My Profile",
    settingsTitle: "Settings",
    languageTitle: "Language",
    notificationsTitle: "Notifications",
    supportTitle: "Support",
    logoutTitle: "Sign Out",
    appVersion: "WasteLink v1.0.0",
    adminTitle: "WasteLink Admin",
    adminSubtitle: "Dispatcher Console",
    adminNavMap: "Live Map",
    adminNavUsers: "Users",
    adminNavAnalytics: "Analytics",
    totalToday: "Today",
    totalWeek: "This Week",
    avgResolution: "Avg. Resolution",
    exportCSV: "Export CSV",
    searchUsers: "Search users…",
    deactivate: "Deactivate",
    activate: "Activate",
    continueBtn: "Continue",
    addNotes: "Add notes",
    photoAdd: "Add Photo",
    cancel: "Cancel",
    detectedAt: "Detected at",
    assignedUnit: "Assigned Unit",
    estimatedArrival: "Estimated Arrival",
    cancelRequest: "Cancel Request",
  },
  sw: {
    appName: "WasteLink",
    tagline: "Ukusanyaji Taka wa Kisasa",
    login: "Ingia",
    register: "Fungua Akaunti",
    phoneLabel: "Nambari ya Simu",
    phonePlaceholder: "+255 712 345 678",
    passwordLabel: "Nenosiri",
    fullNameLabel: "Jina Kamili",
    fullNamePlaceholder: "John Kamau",
    forgotPassword: "Umesahau nenosiri?",
    noAccount: "Huna akaunti?",
    hasAccount: "Una akaunti tayari?",
    navHome: "Nyumbani",
    navHistory: "Historia",
    navProfile: "Wasifu",
    greeting: "Habari za asubuhi",
    homeSubtitle: "Omba ukusanyaji au fuatilia ombi lako.",
    requestCTA: "Omba Ukusanyaji",
    activeRequest: "Ombi Linaloendelea",
    noActiveRequest: "Hakuna maombi yanayoendelea",
    noActiveRequestSub: "Bonyeza kitufe hapo juu kupanga ukusanyaji.",
    selectCategory: "Chagua Aina ya Taka",
    selectCategorySubtitle: "Ni taka gani unahitaji kukusanywa?",
    selectVolume: "Kiasi Kilichokadiriwa",
    selectVolumeSubtitle: "Tusaidie kutuma gari sahihi.",
    confirmLocation: "Thibitisha Mahali",
    locationSubtitle: "Buruta pin kurekebisha mahali pa ukusanyaji.",
    reviewRequest: "Kagua na Wasilisha",
    reviewSubtitle: "Angalia maelezo kabla ya kuwasilisha.",
    submitRequest: "Wasilisha Ombi",
    requestSubmitted: "Ombi Limewasilishwa!",
    requestSubmittedSub: "Ombi lako limerekodiwa na litapangiwa hivi karibuni.",
    backToHome: "Rudi Nyumbani",
    historyTitle: "Maombi Yangu",
    historyEmpty: "Bado hakuna maombi",
    historyEmptySubtitle: "Historia yako ya ukusanyaji itaonekana hapa.",
    statusPending: "Inasubiri",
    statusAssigned: "Imepangiwa",
    statusInTransit: "Inakuja",
    statusCollected: "Imekusanywa",
    statusClosed: "Imefungwa",
    statusCancelled: "Imefutwa",
    categoryHousehold: "Nyumbani",
    categoryConstruction: "Ujenzi",
    categoryGarden: "Bustani / Asili",
    categoryElectronic: "Elektroniki",
    categoryHazardous: "Vifaa Hatari",
    categoryBulk: "Vitu Vikubwa",
    volSmallBag: "Mfuko Mdogo",
    volSmallBagDesc: "Chini ya lita 20",
    volStandardBin: "Ndoo ya Kawaida",
    volStandardBinDesc: "Lita 20 – 80",
    volLargeLoad: "Mzigo Mkubwa",
    volLargeLoadDesc: "Lita 80 – 300",
    volTruckLoad: "Mzigo wa Lori",
    volTruckLoadDesc: "Zaidi ya lita 300",
    addressLabel: "Anwani ya Ukusanyaji",
    notesLabel: "Maelezo (si lazima)",
    notesPlaceholder: "Maelezo yoyote kwa timu ya ukusanyaji…",
    contactSupport: "Wasiliana kupitia WhatsApp",
    timelineTitle: "Mfululizo wa Hali",
    profileTitle: "Wasifu Wangu",
    settingsTitle: "Mipangilio",
    languageTitle: "Lugha",
    notificationsTitle: "Arifa",
    supportTitle: "Msaada",
    logoutTitle: "Toka",
    appVersion: "WasteLink v1.0.0",
    adminTitle: "WasteLink Admin",
    adminSubtitle: "Dashibodi ya Msimamizi",
    adminNavMap: "Ramani",
    adminNavUsers: "Watumiaji",
    adminNavAnalytics: "Takwimu",
    totalToday: "Leo",
    totalWeek: "Wiki Hii",
    avgResolution: "Wastani wa Ufumbuzi",
    exportCSV: "Hamisha CSV",
    searchUsers: "Tafuta watumiaji…",
    deactivate: "Lemaza",
    activate: "Wezesha",
    continueBtn: "Endelea",
    addNotes: "Ongeza maelezo",
    photoAdd: "Ongeza Picha",
    cancel: "Futa",
    detectedAt: "Iliguduliwa saa",
    assignedUnit: "Kitengo Kilichopangwa",
    estimatedArrival: "Wakati wa Kuwasili",
    cancelRequest: "Futa Ombi",
  },
};

const t = (locale: Locale, key: string) => strings[locale][key] ?? key;

// ─── Mock Data ───────────────────────────────────────────────────────────────

const MOCK_REQUESTS: WasteRequest[] = [
  {
    id: "1",
    requestRef: "REQ-000341",
    category: "HOUSEHOLD",
    volumeTier: "STANDARD_BIN",
    status: "IN_TRANSIT",
    address: "14 Uhuru Street, Kariakoo, Dar es Salaam",
    notes: "Gate is on the left side",
    createdAt: "2026-06-29T08:15:00Z",
    assignedUnit: "TRUCK-07",
    statusHistory: [
      { status: "PENDING", time: "08:15", by: "You" },
      { status: "ASSIGNED", time: "08:42", by: "Admin" },
      { status: "IN_TRANSIT", time: "09:10", by: "TRUCK-07" },
    ],
  },
  {
    id: "2",
    requestRef: "REQ-000328",
    category: "GARDEN",
    volumeTier: "LARGE_LOAD",
    status: "COLLECTED",
    address: "Plot 22, Sinza C, Dar es Salaam",
    createdAt: "2026-06-27T14:20:00Z",
    assignedUnit: "TRUCK-03",
    statusHistory: [
      { status: "PENDING", time: "14:20", by: "You" },
      { status: "ASSIGNED", time: "14:55", by: "Admin" },
      { status: "IN_TRANSIT", time: "15:30", by: "TRUCK-03" },
      { status: "COLLECTED", time: "16:05", by: "TRUCK-03" },
    ],
  },
  {
    id: "3",
    requestRef: "REQ-000312",
    category: "ELECTRONIC",
    volumeTier: "SMALL_BAG",
    status: "PENDING",
    address: "Msasani Peninsula, Block 7B, Dar es Salaam",
    createdAt: "2026-06-29T11:00:00Z",
    statusHistory: [{ status: "PENDING", time: "11:00", by: "You" }],
  },
  {
    id: "4",
    requestRef: "REQ-000299",
    category: "CONSTRUCTION",
    volumeTier: "TRUCK_LOAD",
    status: "CANCELLED",
    address: "Mbezi Beach, Lot 88, Dar es Salaam",
    createdAt: "2026-06-25T09:00:00Z",
    statusHistory: [
      { status: "PENDING", time: "09:00", by: "You" },
      { status: "CANCELLED", time: "09:45", by: "You" },
    ],
  },
];

const MOCK_ADMIN_USERS = [
  { id: "u1", name: "Amina Hassan", phone: "+255712001001", lang: "sw", requests: 14, active: true, joined: "2026-01-15" },
  { id: "u2", name: "Peter Oduya", phone: "+255722002002", lang: "en", requests: 7, active: true, joined: "2026-02-20" },
  { id: "u3", name: "Fatuma Juma", phone: "+255712003003", lang: "sw", requests: 22, active: true, joined: "2025-11-05" },
  { id: "u4", name: "Emmanuel Ndeto", phone: "+255733004004", lang: "en", requests: 3, active: false, joined: "2026-03-10" },
  { id: "u5", name: "Zawadi Mwangi", phone: "+255712005005", lang: "sw", requests: 18, active: true, joined: "2026-01-28" },
  { id: "u6", name: "Brian Kamau", phone: "+255721006006", lang: "en", requests: 9, active: true, joined: "2026-04-02" },
];

const analyticsLineData = [
  { day: "Mon", requests: 14 }, { day: "Tue", requests: 22 },
  { day: "Wed", requests: 18 }, { day: "Thu", requests: 27 },
  { day: "Fri", requests: 31 }, { day: "Sat", requests: 19 },
  { day: "Sun", requests: 11 },
];

const categoryPieData = [
  { name: "Household", value: 48, color: "#1A6B38" },
  { name: "Garden", value: 20, color: "#22C55E" },
  { name: "Construction", value: 15, color: "#F97316" },
  { name: "E-Waste", value: 10, color: "#3B82F6" },
  { name: "Hazardous", value: 4, color: "#BA1A1A" },
  { name: "Bulk", value: 3, color: "#F59E0B" },
];

// ─── Status helpers ──────────────────────────────────────────────────────────

const statusConfig: Record<RequestStatus, { color: string; bg: string; icon: React.ReactNode; label: (l: Locale) => string }> = {
  PENDING: { color: "#B45309", bg: "#FEF3C7", icon: <Clock size={12} />, label: (l) => t(l, "statusPending") },
  ASSIGNED: { color: "#1D4ED8", bg: "#DBEAFE", icon: <Package size={12} />, label: (l) => t(l, "statusAssigned") },
  IN_TRANSIT: { color: "#C2410C", bg: "#FFEDD5", icon: <Truck size={12} />, label: (l) => t(l, "statusInTransit") },
  COLLECTED: { color: "#15803D", bg: "#DCFCE7", icon: <Check size={12} />, label: (l) => t(l, "statusCollected") },
  CLOSED: { color: "#374151", bg: "#F3F4F6", icon: <Archive size={12} />, label: (l) => t(l, "statusClosed") },
  CANCELLED: { color: "#991B1B", bg: "#FEE2E2", icon: <X size={12} />, label: (l) => t(l, "statusCancelled") },
};

const categoryConfig: Record<WasteCategory, { icon: React.ReactNode; color: string; bg: string; label: (l: Locale) => string }> = {
  HOUSEHOLD: { icon: <Home size={22} />, color: "#1A6B38", bg: "#DCFCE7", label: (l) => t(l, "categoryHousehold") },
  CONSTRUCTION: { icon: <Layers size={22} />, color: "#C2410C", bg: "#FFEDD5", label: (l) => t(l, "categoryConstruction") },
  GARDEN: { icon: <TreePine size={22} />, color: "#15803D", bg: "#D1FAE5", label: (l) => t(l, "categoryGarden") },
  ELECTRONIC: { icon: <Monitor size={22} />, color: "#1D4ED8", bg: "#DBEAFE", label: (l) => t(l, "categoryElectronic") },
  HAZARDOUS: { icon: <AlertCircle size={22} />, color: "#991B1B", bg: "#FEE2E2", label: (l) => t(l, "categoryHazardous") },
  BULK_ITEMS: { icon: <Package size={22} />, color: "#B45309", bg: "#FEF3C7", label: (l) => t(l, "categoryBulk") },
};

const volumeConfig: Record<VolumeTier, { icon: string; label: (l: Locale) => string; desc: (l: Locale) => string }> = {
  SMALL_BAG: { icon: "🛍️", label: (l) => t(l, "volSmallBag"), desc: (l) => t(l, "volSmallBagDesc") },
  STANDARD_BIN: { icon: "🗑️", label: (l) => t(l, "volStandardBin"), desc: (l) => t(l, "volStandardBinDesc") },
  LARGE_LOAD: { icon: "📦", label: (l) => t(l, "volLargeLoad"), desc: (l) => t(l, "volLargeLoadDesc") },
  TRUCK_LOAD: { icon: "🚛", label: (l) => t(l, "volTruckLoad"), desc: (l) => t(l, "volTruckLoadDesc") },
};

// ─── Shared Components ───────────────────────────────────────────────────────

function StatusBadge({ status, locale }: { status: RequestStatus; locale: Locale }) {
  const cfg = statusConfig[status];
  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium"
      style={{ color: cfg.color, backgroundColor: cfg.bg }}
    >
      {cfg.icon}
      {cfg.label(locale)}
    </span>
  );
}

function AndroidStatusBar({ light = false }: { light?: boolean }) {
  return (
    <div
      className={`flex items-center justify-between px-4 pt-2 pb-1 text-xs font-medium`}
      style={{ color: light ? "rgba(255,255,255,0.9)" : "#1A1C19" }}
    >
      <span>9:41</span>
      <div className="flex items-center gap-1">
        <span>●●●</span>
        <span>WiFi</span>
        <span>🔋</span>
      </div>
    </div>
  );
}

function TopAppBar({
  title,
  subtitle,
  onBack,
  actions,
  transparent = false,
}: {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
  transparent?: boolean;
}) {
  return (
    <div
      className={`flex items-center gap-3 px-2 py-3 ${transparent ? "" : "bg-card border-b border-border"}`}
    >
      {onBack && (
        <button
          onClick={onBack}
          className="p-2 rounded-full hover:bg-muted transition-colors"
        >
          <ChevronLeft size={24} style={{ color: "#1A1C19" }} />
        </button>
      )}
      <div className="flex-1 ml-1">
        <div className="text-base font-medium text-foreground leading-tight">{title}</div>
        {subtitle && <div className="text-xs text-muted-foreground">{subtitle}</div>}
      </div>
      {actions && <div className="flex items-center">{actions}</div>}
    </div>
  );
}

function PrimaryButton({
  label,
  onClick,
  fullWidth = true,
  disabled = false,
  icon,
}: {
  label: string;
  onClick: () => void;
  fullWidth?: boolean;
  disabled?: boolean;
  icon?: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center justify-center gap-2 py-3.5 rounded-2xl font-medium text-sm transition-all active:scale-[0.98] ${fullWidth ? "w-full" : "px-6"}`}
      style={{ backgroundColor: disabled ? "#8DA88A" : "#1A6B38", color: "#fff" }}
    >
      {icon}
      {label}
    </button>
  );
}

function OutlinedButton({ label, onClick, fullWidth = true }: { label: string; onClick: () => void; fullWidth?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center justify-center gap-2 py-3.5 rounded-2xl font-medium text-sm border-2 transition-all active:scale-[0.98] ${fullWidth ? "w-full" : "px-6"}`}
      style={{ borderColor: "#1A6B38", color: "#1A6B38" }}
    >
      {label}
    </button>
  );
}

function TextField({
  label,
  value,
  onChange,
  placeholder,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  type?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{label}</label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-4 py-3.5 rounded-xl text-sm text-foreground outline-none border border-border focus:border-primary transition-colors"
        style={{ backgroundColor: "#EEF3EC" }}
      />
    </div>
  );
}

// ─── Bottom Navigation ───────────────────────────────────────────────────────

function BottomNav({
  activeTab,
  onTabChange,
  locale,
  badgeCount,
}: {
  activeTab: Tab;
  onTabChange: (t: Tab) => void;
  locale: Locale;
  badgeCount: number;
}) {
  const tabs: { id: Tab; icon: React.ReactNode; label: string }[] = [
    { id: "home", icon: <Home size={22} />, label: t(locale, "navHome") },
    { id: "history", icon: <History size={22} />, label: t(locale, "navHistory") },
    { id: "profile", icon: <User size={22} />, label: t(locale, "navProfile") },
  ];

  return (
    <div className="flex items-center justify-around bg-card border-t border-border pb-2 pt-1">
      {tabs.map((tab) => {
        const active = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            className="flex flex-col items-center gap-0.5 py-1 relative flex-1"
          >
            <div
              className="relative flex items-center justify-center w-14 h-8 rounded-full transition-all"
              style={{ backgroundColor: active ? "#DCE8D8" : "transparent" }}
            >
              <span style={{ color: active ? "#1A6B38" : "#52634F" }}>{tab.icon}</span>
              {tab.id === "home" && badgeCount > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-destructive text-white text-[9px] font-bold flex items-center justify-center">
                  {badgeCount}
                </span>
              )}
            </div>
            <span
              className="text-[10px] font-medium"
              style={{ color: active ? "#1A6B38" : "#52634F" }}
            >
              {tab.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

// ─── Screens: Auth ───────────────────────────────────────────────────────────

function SplashScreen({ onContinue, onAdmin }: { onContinue: () => void; onAdmin: () => void }) {
  useEffect(() => {
    const t = setTimeout(onContinue, 2200);
    return () => clearTimeout(t);
  }, [onContinue]);

  return (
    <div className="flex flex-col items-center justify-center h-full" style={{ background: "linear-gradient(160deg, #1A6B38 0%, #134F2A 100%)" }}>
      <div className="flex flex-col items-center gap-4 mb-16">
        <div className="w-20 h-20 rounded-3xl flex items-center justify-center shadow-2xl" style={{ backgroundColor: "rgba(255,255,255,0.15)" }}>
          <Trash2 size={40} color="white" />
        </div>
        <div className="text-center">
          <h1 className="text-3xl font-bold text-white tracking-tight">WasteLink</h1>
          <p className="text-sm mt-1" style={{ color: "rgba(255,255,255,0.7)" }}>Smart Waste Collection</p>
        </div>
      </div>
      <div className="absolute bottom-12 left-0 right-0 flex flex-col items-center gap-2">
        <div className="w-6 h-6 border-2 border-white border-t-transparent rounded-full animate-spin opacity-50" />
        <button onClick={onAdmin} className="text-xs mt-4" style={{ color: "rgba(255,255,255,0.4)" }}>
          Admin Portal →
        </button>
      </div>
    </div>
  );
}

function LoginScreen({ onLogin, onRegister, locale, onLocaleChange }: {
  onLogin: () => void;
  onRegister: () => void;
  locale: Locale;
  onLocaleChange: (l: Locale) => void;
}) {
  const [phone, setPhone] = useState("+255712345678");
  const [password, setPassword] = useState("password123");

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <div className="flex-1 flex flex-col px-5 pt-4 pb-6 overflow-y-auto">
        <div className="flex justify-end mb-6">
          <button
            onClick={() => onLocaleChange(locale === "en" ? "sw" : "en")}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-border text-xs font-medium text-muted-foreground"
          >
            <Globe size={13} />
            {locale === "en" ? "Kiswahili" : "English"}
          </button>
        </div>

        <div className="flex flex-col items-start mb-8">
          <div className="w-12 h-12 rounded-2xl flex items-center justify-center mb-4" style={{ backgroundColor: "#1A6B38" }}>
            <Trash2 size={24} color="white" />
          </div>
          <h1 className="text-2xl font-bold text-foreground">WasteLink</h1>
          <p className="text-sm text-muted-foreground mt-1">{t(locale, "tagline")}</p>
        </div>

        <div className="flex flex-col gap-4 mb-6">
          <TextField label={t(locale, "phoneLabel")} value={phone} onChange={setPhone} placeholder={t(locale, "phonePlaceholder")} type="tel" />
          <TextField label={t(locale, "passwordLabel")} value={password} onChange={setPassword} placeholder="••••••••" type="password" />
          <button className="text-right text-xs" style={{ color: "#1A6B38" }}>{t(locale, "forgotPassword")}</button>
        </div>

        <PrimaryButton label={t(locale, "login")} onClick={onLogin} />

        <div className="flex items-center gap-2 my-5">
          <div className="flex-1 h-px bg-border" />
          <span className="text-xs text-muted-foreground">{t(locale, "noAccount")}</span>
          <div className="flex-1 h-px bg-border" />
        </div>

        <OutlinedButton label={t(locale, "register")} onClick={onRegister} />
      </div>
    </div>
  );
}

// ─── Home Tab ────────────────────────────────────────────────────────────────

function HomeScreen({ locale, onNewRequest, onViewRequest }: {
  locale: Locale;
  onNewRequest: () => void;
  onViewRequest: (r: WasteRequest) => void;
}) {
  const activeReq = MOCK_REQUESTS.find((r) => r.status === "IN_TRANSIT" || r.status === "ASSIGNED");

  return (
    <div className="flex flex-col h-full overflow-y-auto bg-background">
      {/* Hero strip */}
      <div className="px-5 pt-4 pb-5" style={{ background: "linear-gradient(135deg, #1A6B38 0%, #0F4524 100%)" }}>
        <AndroidStatusBar light />
        <div className="mt-2 mb-4">
          <p className="text-sm font-normal" style={{ color: "rgba(255,255,255,0.75)" }}>{t(locale, "greeting")}, Amina 👋</p>
          <h1 className="text-xl font-semibold text-white mt-0.5">{t(locale, "homeSubtitle")}</h1>
        </div>
        <button
          onClick={onNewRequest}
          className="flex items-center justify-center gap-2 w-full py-3.5 rounded-2xl font-medium text-sm"
          style={{ backgroundColor: "rgba(255,255,255,0.15)", color: "#fff", border: "1px solid rgba(255,255,255,0.25)" }}
        >
          <Plus size={18} />
          {t(locale, "requestCTA")}
        </button>
      </div>

      <div className="flex flex-col gap-4 px-4 py-4">
        {/* Active request card */}
        {activeReq ? (
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">{t(locale, "activeRequest")}</p>
            <button
              onClick={() => onViewRequest(activeReq)}
              className="w-full bg-card rounded-2xl p-4 border border-border text-left active:scale-[0.99] transition-transform"
              style={{ boxShadow: "0 1px 4px rgba(0,0,0,0.07)" }}
            >
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs text-muted-foreground font-mono">{activeReq.requestRef}</p>
                  <p className="text-sm font-semibold text-foreground mt-0.5">
                    {categoryConfig[activeReq.category].label(locale)}
                  </p>
                </div>
                <StatusBadge status={activeReq.status} locale={locale} />
              </div>
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <MapPin size={12} style={{ color: "#1A6B38" }} />
                <span className="truncate">{activeReq.address}</span>
              </div>
              {activeReq.assignedUnit && (
                <div className="mt-2 flex items-center gap-1.5">
                  <div className="w-1.5 h-1.5 rounded-full bg-orange-400 animate-pulse" />
                  <span className="text-xs text-muted-foreground">{t(locale, "assignedUnit")}: <strong>{activeReq.assignedUnit}</strong></span>
                </div>
              )}
              <div className="flex items-center justify-end mt-2">
                <span className="text-xs font-medium" style={{ color: "#1A6B38" }}>View details</span>
                <ChevronRight size={14} style={{ color: "#1A6B38" }} />
              </div>
            </button>
          </div>
        ) : (
          <div className="bg-card rounded-2xl p-5 border border-border text-center">
            <div className="w-12 h-12 rounded-full mx-auto flex items-center justify-center mb-3" style={{ backgroundColor: "#E8F0E6" }}>
              <Trash2 size={22} style={{ color: "#52634F" }} />
            </div>
            <p className="text-sm font-medium text-foreground">{t(locale, "noActiveRequest")}</p>
            <p className="text-xs text-muted-foreground mt-1">{t(locale, "noActiveRequestSub")}</p>
          </div>
        )}

        {/* Quick stats */}
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">Recent Activity</p>
          <div className="grid grid-cols-3 gap-3">
            {[
              { label: "Total", value: "4", color: "#1A6B38" },
              { label: "Active", value: "2", color: "#C2410C" },
              { label: "Collected", value: "1", color: "#15803D" },
            ].map((stat) => (
              <div key={stat.label} className="bg-card rounded-xl p-3 border border-border text-center">
                <p className="text-xl font-bold" style={{ color: stat.color }}>{stat.value}</p>
                <p className="text-[10px] text-muted-foreground mt-0.5">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Recent requests */}
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">Recent</p>
          <div className="flex flex-col gap-2">
            {MOCK_REQUESTS.slice(0, 3).map((req) => (
              <button
                key={req.id}
                onClick={() => onViewRequest(req)}
                className="w-full bg-card rounded-xl p-3.5 border border-border flex items-center gap-3 text-left active:scale-[0.99] transition-transform"
              >
                <div className="w-9 h-9 rounded-full flex items-center justify-center shrink-0"
                  style={{ backgroundColor: categoryConfig[req.category].bg }}>
                  <span style={{ color: categoryConfig[req.category].color }}>{categoryConfig[req.category].icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-mono text-muted-foreground">{req.requestRef}</p>
                    <StatusBadge status={req.status} locale={locale} />
                  </div>
                  <p className="text-sm font-medium text-foreground truncate">{categoryConfig[req.category].label(locale)}</p>
                </div>
                <ChevronRight size={16} style={{ color: "#8DA88A" }} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── New Request Flow ────────────────────────────────────────────────────────

function NewRequestCategory({ locale, onBack, onNext }: {
  locale: Locale;
  onBack: () => void;
  onNext: (cat: WasteCategory) => void;
}) {
  const categories: WasteCategory[] = ["HOUSEHOLD", "CONSTRUCTION", "GARDEN", "ELECTRONIC", "HAZARDOUS", "BULK_ITEMS"];
  const [selected, setSelected] = useState<WasteCategory | null>(null);

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <TopAppBar title={t(locale, "selectCategory")} subtitle={t(locale, "selectCategorySubtitle")} onBack={onBack} />
      <div className="flex-1 overflow-y-auto px-4 py-4">
        <div className="grid grid-cols-2 gap-3">
          {categories.map((cat) => {
            const cfg = categoryConfig[cat];
            const active = selected === cat;
            return (
              <button
                key={cat}
                onClick={() => setSelected(cat)}
                className="flex flex-col items-center gap-2.5 p-4 rounded-2xl border-2 transition-all active:scale-95"
                style={{
                  borderColor: active ? "#1A6B38" : "rgba(0,0,0,0.1)",
                  backgroundColor: active ? "#E8F5EC" : "#FFFFFF",
                }}
              >
                <div className="w-12 h-12 rounded-2xl flex items-center justify-center"
                  style={{ backgroundColor: cfg.bg }}>
                  <span style={{ color: cfg.color }}>{cfg.icon}</span>
                </div>
                <span className="text-xs font-medium text-center leading-tight text-foreground">{cfg.label(locale)}</span>
                {active && <Check size={16} style={{ color: "#1A6B38" }} />}
              </button>
            );
          })}
        </div>
      </div>
      <div className="px-4 pb-6 pt-3 bg-background border-t border-border">
        <PrimaryButton label={t(locale, "continueBtn")} onClick={() => selected && onNext(selected)} disabled={!selected} />
      </div>
    </div>
  );
}

function NewRequestVolume({ locale, category, onBack, onNext }: {
  locale: Locale;
  category: WasteCategory;
  onBack: () => void;
  onNext: (vol: VolumeTier) => void;
}) {
  const volumes: VolumeTier[] = ["SMALL_BAG", "STANDARD_BIN", "LARGE_LOAD", "TRUCK_LOAD"];
  const [selected, setSelected] = useState<VolumeTier | null>(null);

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <TopAppBar title={t(locale, "selectVolume")} subtitle={t(locale, "selectVolumeSubtitle")} onBack={onBack} />
      <div className="flex-1 overflow-y-auto px-4 py-4">
        {/* Selected category recap */}
        <div className="flex items-center gap-2 mb-4 px-3 py-2 rounded-xl" style={{ backgroundColor: categoryConfig[category].bg }}>
          <span style={{ color: categoryConfig[category].color }}>{categoryConfig[category].icon}</span>
          <span className="text-sm font-medium" style={{ color: categoryConfig[category].color }}>{categoryConfig[category].label(locale)}</span>
        </div>

        <div className="flex flex-col gap-3">
          {volumes.map((vol) => {
            const cfg = volumeConfig[vol];
            const active = selected === vol;
            return (
              <button
                key={vol}
                onClick={() => setSelected(vol)}
                className="flex items-center gap-4 p-4 rounded-2xl border-2 transition-all text-left"
                style={{
                  borderColor: active ? "#1A6B38" : "rgba(0,0,0,0.1)",
                  backgroundColor: active ? "#E8F5EC" : "#FFFFFF",
                }}
              >
                <span className="text-3xl">{cfg.icon}</span>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-foreground">{cfg.label(locale)}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{cfg.desc(locale)}</p>
                </div>
                <div
                  className="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0"
                  style={{ borderColor: active ? "#1A6B38" : "#8DA88A", backgroundColor: active ? "#1A6B38" : "transparent" }}
                >
                  {active && <Check size={11} color="white" />}
                </div>
              </button>
            );
          })}
        </div>
      </div>
      <div className="px-4 pb-6 pt-3 bg-background border-t border-border">
        <PrimaryButton label={t(locale, "continueBtn")} onClick={() => selected && onNext(selected)} disabled={!selected} />
      </div>
    </div>
  );
}

function NewRequestLocation({ locale, onBack, onNext }: {
  locale: Locale;
  onBack: () => void;
  onNext: (address: string, notes: string) => void;
}) {
  const [notes, setNotes] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const address = "14 Uhuru Street, Kariakoo, Dar es Salaam";

  useEffect(() => {
    const t = setTimeout(() => setConfirmed(true), 1500);
    return () => clearTimeout(t);
  }, []);

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <TopAppBar title={t(locale, "confirmLocation")} subtitle={t(locale, "locationSubtitle")} onBack={onBack} />

      {/* Map placeholder */}
      <div className="relative mx-4 mt-2 rounded-2xl overflow-hidden border border-border" style={{ height: 220 }}>
        <div className="absolute inset-0"
          style={{
            background: "linear-gradient(135deg, #c8e6c9 0%, #a5d6a7 30%, #81c784 60%, #66bb6a 100%)",
          }}>
          {/* Fake map grid */}
          {[...Array(8)].map((_, i) => (
            <div key={i} className="absolute w-full border-t" style={{ top: `${i * 30}px`, borderColor: "rgba(0,0,0,0.06)" }} />
          ))}
          {[...Array(6)].map((_, i) => (
            <div key={i} className="absolute h-full border-l" style={{ left: `${i * 60}px`, borderColor: "rgba(0,0,0,0.06)" }} />
          ))}
          {/* Streets */}
          <div className="absolute top-16 left-0 right-0 h-4 bg-white opacity-50" />
          <div className="absolute top-0 bottom-0 left-24 w-3 bg-white opacity-50" />
          <div className="absolute top-0 bottom-0 right-16 w-3 bg-white opacity-40" />
          <div className="absolute bottom-12 left-0 right-0 h-3 bg-white opacity-40" />
        </div>
        {/* Pin */}
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="flex flex-col items-center">
            <div className="w-8 h-8 rounded-full flex items-center justify-center shadow-lg" style={{ backgroundColor: "#1A6B38" }}>
              <MapPin size={18} color="white" />
            </div>
            <div className="w-2 h-2 rounded-full mt-0.5" style={{ backgroundColor: "#1A6B38" }} />
          </div>
        </div>
        {/* GPS indicator */}
        <div className="absolute top-3 right-3 w-8 h-8 rounded-full bg-white shadow flex items-center justify-center">
          <Navigation size={16} style={{ color: confirmed ? "#1A6B38" : "#8DA88A" }} />
        </div>
        {/* Accuracy chip */}
        {confirmed && (
          <div className="absolute bottom-3 left-3 flex items-center gap-1.5 bg-white rounded-full px-2.5 py-1 shadow text-xs">
            <div className="w-1.5 h-1.5 rounded-full bg-green-500" />
            <span className="text-foreground font-medium">±8m accuracy</span>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        {/* Address */}
        <div className="bg-card rounded-xl p-4 border border-border">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">{t(locale, "addressLabel")}</p>
          {confirmed ? (
            <div className="flex items-start gap-2">
              <MapPin size={14} style={{ color: "#1A6B38", marginTop: 2 }} />
              <p className="text-sm text-foreground leading-relaxed">{address}</p>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 border-2 border-green-500 border-t-transparent rounded-full animate-spin" />
              <span className="text-sm text-muted-foreground">Detecting location…</span>
            </div>
          )}
        </div>

        {/* Notes */}
        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{t(locale, "notesLabel")}</label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={t(locale, "notesPlaceholder")}
            maxLength={280}
            rows={3}
            className="w-full px-4 py-3 rounded-xl text-sm text-foreground outline-none border border-border focus:border-primary transition-colors resize-none"
            style={{ backgroundColor: "#EEF3EC" }}
          />
          <p className="text-right text-xs text-muted-foreground">{notes.length}/280</p>
        </div>

        {/* Photos */}
        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{t(locale, "photoAdd")}</label>
          <div className="flex gap-2">
            {[0, 1, 2].map((i) => (
              <div key={i} className="w-20 h-20 rounded-xl border-2 border-dashed flex flex-col items-center justify-center gap-1"
                style={{ borderColor: "rgba(26,107,56,0.3)", backgroundColor: "#F4FAF4" }}>
                <Camera size={18} style={{ color: "#8DA88A" }} />
                <span className="text-[9px] text-muted-foreground">{i === 0 ? "Add" : ""}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="px-4 pb-6 pt-3 bg-background border-t border-border">
        <PrimaryButton label={t(locale, "continueBtn")} onClick={() => onNext(address, notes)} disabled={!confirmed} />
      </div>
    </div>
  );
}

function NewRequestConfirm({ locale, category, volumeTier, address, notes, onBack, onSubmit }: {
  locale: Locale;
  category: WasteCategory;
  volumeTier: VolumeTier;
  address: string;
  notes: string;
  onBack: () => void;
  onSubmit: () => void;
}) {
  const catCfg = categoryConfig[category];
  const volCfg = volumeConfig[volumeTier];

  const rows = [
    { label: t(locale, "selectCategory"), value: catCfg.label(locale), icon: catCfg.icon, color: catCfg.color },
    { label: t(locale, "selectVolume"), value: volCfg.label(locale), icon: <span>{volCfg.icon}</span>, color: "#1A6B38" },
    { label: t(locale, "addressLabel"), value: address, icon: <MapPin size={16} />, color: "#1A6B38" },
  ];

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <TopAppBar title={t(locale, "reviewRequest")} subtitle={t(locale, "reviewSubtitle")} onBack={onBack} />
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        <div className="bg-card rounded-2xl border border-border overflow-hidden">
          {rows.map((row, i) => (
            <div key={i} className={`flex items-start gap-3 p-4 ${i < rows.length - 1 ? "border-b border-border" : ""}`}>
              <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0" style={{ backgroundColor: "#E8F0E6", color: row.color }}>
                {row.icon}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">{row.label}</p>
                <p className="text-sm text-foreground mt-0.5 leading-snug">{row.value}</p>
              </div>
            </div>
          ))}
          {notes && (
            <div className="flex items-start gap-3 p-4 border-t border-border">
              <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0" style={{ backgroundColor: "#E8F0E6" }}>
                <Edit3 size={14} style={{ color: "#1A6B38" }} />
              </div>
              <div>
                <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">{t(locale, "notesLabel")}</p>
                <p className="text-sm text-foreground mt-0.5">{notes}</p>
              </div>
            </div>
          )}
        </div>

        <div className="flex items-start gap-2 px-3 py-3 rounded-xl" style={{ backgroundColor: "#FEF9EC" }}>
          <AlertCircle size={14} style={{ color: "#B45309", marginTop: 1 }} />
          <p className="text-xs" style={{ color: "#92400E" }}>
            Your GPS coordinates will be submitted and stored with this request. Location cannot be changed after submission.
          </p>
        </div>
      </div>
      <div className="px-4 pb-6 pt-3 bg-background border-t border-border">
        <PrimaryButton label={t(locale, "submitRequest")} onClick={onSubmit} icon={<Zap size={16} />} />
      </div>
    </div>
  );
}

function NewRequestSubmitted({ locale, onDone }: { locale: Locale; onDone: () => void }) {
  return (
    <div className="flex flex-col h-full bg-background items-center justify-center px-8">
      <div className="flex flex-col items-center text-center gap-4">
        <div className="w-20 h-20 rounded-full flex items-center justify-center" style={{ backgroundColor: "#DCFCE7" }}>
          <CheckCircle2 size={44} style={{ color: "#15803D" }} />
        </div>
        <div>
          <h2 className="text-xl font-bold text-foreground">{t(locale, "requestSubmitted")}</h2>
          <p className="text-sm text-muted-foreground mt-1.5 leading-relaxed">{t(locale, "requestSubmittedSub")}</p>
        </div>
        <div className="bg-card rounded-2xl px-6 py-3 border border-border mt-2">
          <p className="text-xs text-muted-foreground">Request Reference</p>
          <p className="text-2xl font-bold tracking-wider mt-1" style={{ color: "#1A6B38", fontFamily: "Roboto Mono" }}>REQ-000342</p>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground mt-1">
          <Clock size={12} />
          <span>Estimated response: 2 – 4 hours</span>
        </div>
      </div>
      <div className="absolute bottom-8 left-4 right-4">
        <PrimaryButton label={t(locale, "backToHome")} onClick={onDone} />
      </div>
    </div>
  );
}

// ─── History Tab ─────────────────────────────────────────────────────────────

function HistoryScreen({ locale, onViewRequest }: { locale: Locale; onViewRequest: (r: WasteRequest) => void }) {
  const [search, setSearch] = useState("");

  return (
    <div className="flex flex-col h-full bg-background">
      <div className="bg-card border-b border-border px-4 pt-3 pb-3">
        <AndroidStatusBar />
        <h1 className="text-lg font-semibold text-foreground mb-2">{t(locale, "historyTitle")}</h1>
        <div className="flex items-center gap-2 bg-input-background rounded-xl px-3 py-2.5">
          <Search size={16} style={{ color: "#52634F" }} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search requests…"
            className="flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-2">
        {MOCK_REQUESTS.map((req) => {
          const catCfg = categoryConfig[req.category];
          const date = new Date(req.createdAt).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
          return (
            <button
              key={req.id}
              onClick={() => onViewRequest(req)}
              className="w-full bg-card rounded-2xl p-4 border border-border text-left active:scale-[0.99] transition-transform flex gap-3"
              style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}
            >
              {/* Category color bar */}
              <div className="w-1 rounded-full self-stretch" style={{ backgroundColor: catCfg.color }} />
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-xs font-mono text-muted-foreground">{req.requestRef}</p>
                  <StatusBadge status={req.status} locale={locale} />
                </div>
                <p className="text-sm font-semibold text-foreground">{catCfg.label(locale)}</p>
                <div className="flex items-center gap-1.5 mt-1">
                  <MapPin size={10} style={{ color: "#8DA88A" }} />
                  <p className="text-xs text-muted-foreground truncate">{req.address}</p>
                </div>
              </div>
              <div className="flex flex-col items-end justify-between shrink-0">
                <span className="text-[10px] text-muted-foreground">{date}</span>
                <ChevronRight size={16} style={{ color: "#8DA88A" }} />
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ─── Request Detail ──────────────────────────────────────────────────────────

function RequestDetailScreen({ request, locale, onBack }: { request: WasteRequest; locale: Locale; onBack: () => void }) {
  const catCfg = categoryConfig[request.category];
  const allStatuses: RequestStatus[] = ["PENDING", "ASSIGNED", "IN_TRANSIT", "COLLECTED", "CLOSED"];
  const currentIdx = allStatuses.indexOf(request.status);

  return (
    <div className="flex flex-col h-full bg-background">
      <AndroidStatusBar />
      <TopAppBar
        title={request.requestRef}
        subtitle={catCfg.label(locale)}
        onBack={onBack}
        actions={
          <button className="p-2 rounded-full hover:bg-muted">
            <MoreVertical size={20} style={{ color: "#52634F" }} />
          </button>
        }
      />

      <div className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-4">
        {/* Mini map */}
        <div className="relative rounded-2xl overflow-hidden border border-border" style={{ height: 160 }}>
          <div className="absolute inset-0"
            style={{ background: "linear-gradient(135deg, #b2dfdb 0%, #80cbc4 40%, #4db6ac 100%)" }}>
            {[...Array(6)].map((_, i) => (
              <div key={i} className="absolute w-full border-t" style={{ top: `${i * 28}px`, borderColor: "rgba(0,0,0,0.08)" }} />
            ))}
            {[...Array(5)].map((_, i) => (
              <div key={i} className="absolute h-full border-l" style={{ left: `${i * 60}px`, borderColor: "rgba(0,0,0,0.06)" }} />
            ))}
            <div className="absolute top-12 left-0 right-0 h-3 bg-white opacity-40" />
            <div className="absolute top-0 bottom-0 left-20 w-3 bg-white opacity-40" />
          </div>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex flex-col items-center drop-shadow-lg">
              <div className="w-8 h-8 rounded-full flex items-center justify-center" style={{ backgroundColor: catCfg.color }}>
                <MapPin size={16} color="white" />
              </div>
              <div className="w-2 h-2 rounded-full mt-0.5" style={{ backgroundColor: catCfg.color }} />
            </div>
          </div>
        </div>

        {/* Status + Address */}
        <div className="bg-card rounded-2xl border border-border overflow-hidden">
          <div className="p-4 flex items-center justify-between border-b border-border">
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Current Status</p>
            <StatusBadge status={request.status} locale={locale} />
          </div>
          <div className="p-4 flex items-start gap-2 border-b border-border">
            <MapPin size={14} style={{ color: "#1A6B38", marginTop: 2 }} />
            <div>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide mb-0.5">{t(locale, "addressLabel")}</p>
              <p className="text-sm text-foreground leading-relaxed">{request.address}</p>
            </div>
          </div>
          <div className="grid grid-cols-2 divide-x divide-border">
            <div className="p-4">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide mb-1">Type</p>
              <div className="flex items-center gap-1.5">
                <span style={{ color: catCfg.color }}>{catCfg.icon}</span>
                <p className="text-sm text-foreground">{catCfg.label(locale)}</p>
              </div>
            </div>
            <div className="p-4">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide mb-1">Volume</p>
              <p className="text-sm text-foreground">{volumeConfig[request.volumeTier].label(locale)}</p>
            </div>
          </div>
          {request.assignedUnit && (
            <div className="p-4 border-t border-border flex items-center gap-2">
              <Truck size={14} style={{ color: "#1A6B38" }} />
              <div>
                <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">{t(locale, "assignedUnit")}</p>
                <p className="text-sm font-medium text-foreground">{request.assignedUnit}</p>
              </div>
            </div>
          )}
        </div>

        {/* Timeline */}
        <div className="bg-card rounded-2xl border border-border p-4">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-4">{t(locale, "timelineTitle")}</p>
          <div className="flex flex-col gap-0">
            {allStatuses.filter(s => s !== "CLOSED").map((s, idx) => {
              const done = idx <= currentIdx && request.status !== "CANCELLED";
              const current = allStatuses[currentIdx] === s;
              const histEntry = request.statusHistory.find((h) => h.status === s);
              return (
                <div key={s} className="flex items-start gap-3">
                  <div className="flex flex-col items-center">
                    <div
                      className="w-7 h-7 rounded-full flex items-center justify-center shrink-0 border-2 transition-all"
                      style={{
                        borderColor: done ? "#1A6B38" : "#DCE8D8",
                        backgroundColor: done ? (current ? "#1A6B38" : "#DCFCE7") : "white",
                      }}
                    >
                      {done && !current && <Check size={13} style={{ color: "#1A6B38" }} />}
                      {current && <div className="w-2.5 h-2.5 rounded-full bg-white" />}
                      {!done && <Circle size={10} style={{ color: "#DCE8D8" }} />}
                    </div>
                    {idx < allStatuses.filter(s => s !== "CLOSED").length - 1 && (
                      <div className="w-0.5 h-7 my-0.5" style={{ backgroundColor: done && idx < currentIdx ? "#1A6B38" : "#DCE8D8" }} />
                    )}
                  </div>
                  <div className="pb-3 pt-0.5 flex-1">
                    <p className="text-sm font-medium text-foreground">{statusConfig[s].label(locale)}</p>
                    {histEntry && <p className="text-xs text-muted-foreground mt-0.5">{histEntry.time} · by {histEntry.by}</p>}
                    {!histEntry && <p className="text-xs text-muted-foreground mt-0.5">—</p>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* WhatsApp CTA */}
        <button
          className="w-full flex items-center justify-center gap-2.5 py-3.5 rounded-2xl font-medium text-sm border-2 transition-all active:scale-[0.98]"
          style={{ borderColor: "#25D366", color: "#25D366" }}
        >
          <MessageCircle size={18} />
          {t(locale, "contactSupport")}
        </button>

        {request.status === "PENDING" && (
          <button className="w-full text-center text-sm py-2 text-destructive font-medium">{t(locale, "cancelRequest")}</button>
        )}

        <div className="h-2" />
      </div>
    </div>
  );
}

// ─── Profile Tab ─────────────────────────────────────────────────────────────

function ProfileScreen({ locale, onLocaleChange, onLogout }: {
  locale: Locale;
  onLocaleChange: (l: Locale) => void;
  onLogout: () => void;
}) {
  return (
    <div className="flex flex-col h-full bg-background">
      <div className="bg-card border-b border-border px-4 pt-3 pb-4">
        <AndroidStatusBar />
        <h1 className="text-lg font-semibold text-foreground">{t(locale, "profileTitle")}</h1>
      </div>
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        {/* Avatar */}
        <div className="bg-card rounded-2xl p-4 border border-border flex items-center gap-4">
          <div className="w-14 h-14 rounded-full flex items-center justify-center text-white text-xl font-bold" style={{ backgroundColor: "#1A6B38" }}>
            A
          </div>
          <div>
            <p className="text-base font-semibold text-foreground">Amina Hassan</p>
            <p className="text-xs text-muted-foreground mt-0.5">+255 712 001 001</p>
            <p className="text-xs mt-1 px-2 py-0.5 rounded-full inline-block" style={{ backgroundColor: "#E8F0E6", color: "#1A6B38" }}>Resident</p>
          </div>
          <button className="ml-auto p-2 rounded-full bg-muted">
            <Edit3 size={16} style={{ color: "#52634F" }} />
          </button>
        </div>

        {/* Settings groups */}
        <div className="bg-card rounded-2xl border border-border overflow-hidden">
          <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest px-4 pt-3 pb-1">Preferences</p>

          {/* Language */}
          <div className="px-4 py-3 border-b border-border">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2.5">
                <Globe size={16} style={{ color: "#1A6B38" }} />
                <span className="text-sm font-medium text-foreground">{t(locale, "languageTitle")}</span>
              </div>
            </div>
            <div className="flex gap-2">
              {(["en", "sw"] as Locale[]).map((l) => (
                <button
                  key={l}
                  onClick={() => onLocaleChange(l)}
                  className="flex-1 py-2 rounded-xl text-xs font-medium border-2 transition-all"
                  style={{
                    borderColor: locale === l ? "#1A6B38" : "rgba(0,0,0,0.1)",
                    backgroundColor: locale === l ? "#E8F5EC" : "white",
                    color: locale === l ? "#1A6B38" : "#52634F",
                  }}
                >
                  {l === "en" ? "🇬🇧 English" : "🇹🇿 Kiswahili"}
                </button>
              ))}
            </div>
          </div>

          {/* Notifications */}
          <button className="w-full flex items-center gap-3 px-4 py-3.5 border-b border-border">
            <Bell size={16} style={{ color: "#1A6B38" }} />
            <span className="flex-1 text-sm font-medium text-foreground text-left">{t(locale, "notificationsTitle")}</span>
            <div className="w-10 h-6 rounded-full flex items-center px-0.5" style={{ backgroundColor: "#1A6B38" }}>
              <div className="w-5 h-5 rounded-full bg-white shadow ml-auto" />
            </div>
          </button>
        </div>

        <div className="bg-card rounded-2xl border border-border overflow-hidden">
          <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest px-4 pt-3 pb-1">Support</p>

          <button className="w-full flex items-center gap-3 px-4 py-3.5 border-b border-border" style={{ color: "#25D366" }}>
            <MessageCircle size={16} />
            <span className="flex-1 text-sm font-medium text-left">{t(locale, "supportTitle")} via WhatsApp</span>
            <ChevronRight size={16} style={{ color: "#8DA88A" }} />
          </button>

          <button className="w-full flex items-center gap-3 px-4 py-3.5">
            <Shield size={16} style={{ color: "#52634F" }} />
            <span className="flex-1 text-sm font-medium text-foreground text-left">Privacy Policy</span>
            <ChevronRight size={16} style={{ color: "#8DA88A" }} />
          </button>
        </div>

        {/* Logout */}
        <button
          onClick={onLogout}
          className="w-full flex items-center gap-3 bg-card rounded-2xl px-4 py-3.5 border border-border"
        >
          <LogOut size={16} style={{ color: "#BA1A1A" }} />
          <span className="text-sm font-medium" style={{ color: "#BA1A1A" }}>{t(locale, "logoutTitle")}</span>
        </button>

        <p className="text-center text-[10px] text-muted-foreground pb-2">{t(locale, "appVersion")}</p>
      </div>
    </div>
  );
}

// ─── Admin Screens ───────────────────────────────────────────────────────────

function AdminLogin({ onLogin, onBack }: { onLogin: () => void; onBack: () => void }) {
  const [email, setEmail] = useState("admin@wastelink.co.tz");
  const [password, setPassword] = useState("admin123");

  return (
    <div className="flex flex-col h-full" style={{ background: "#0F4524" }}>
      <AndroidStatusBar light />
      <button onClick={onBack} className="flex items-center gap-1 px-4 mt-1 text-sm" style={{ color: "rgba(255,255,255,0.7)" }}>
        <ChevronLeft size={16} /> Back
      </button>
      <div className="flex-1 flex flex-col justify-center px-6">
        <div className="flex items-center gap-3 mb-8">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ backgroundColor: "rgba(255,255,255,0.15)" }}>
            <Shield size={20} color="white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">WasteLink Admin</h1>
            <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>Dispatcher Console</p>
          </div>
        </div>

        <div className="bg-white rounded-2xl p-5 flex flex-col gap-4 shadow-2xl">
          <TextField label="Email Address" value={email} onChange={setEmail} placeholder="admin@wastelink.co.tz" />
          <TextField label="Password" value={password} onChange={setPassword} placeholder="••••••••" type="password" />
          <PrimaryButton label="Sign In to Admin" onClick={onLogin} icon={<Shield size={16} />} />
        </div>
      </div>
    </div>
  );
}

function AdminMapView({ onTabChange }: { onTabChange: (t: AdminTab) => void }) {
  const [selected, setSelected] = useState<WasteRequest | null>(null);

  return (
    <div className="flex flex-col h-full bg-background">
      <div className="bg-card border-b border-border px-4 pt-2 pb-3">
        <AndroidStatusBar />
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-base font-semibold text-foreground">Live Request Map</h1>
            <p className="text-xs text-muted-foreground">Dar es Salaam · 3 active</p>
          </div>
          <div className="flex items-center gap-2">
            <button className="p-2 rounded-full bg-muted"><Filter size={16} style={{ color: "#52634F" }} /></button>
            <button className="p-2 rounded-full bg-muted"><RefreshCw size={16} style={{ color: "#52634F" }} /></button>
          </div>
        </div>
      </div>

      {/* Map */}
      <div className="relative flex-1 overflow-hidden" style={{ minHeight: 220 }}>
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(135deg, #E8F5E9 0%, #C8E6C9 40%, #A5D6A7 80%, #81C784 100%)" }}>
          {[...Array(12)].map((_, i) => (
            <div key={i} className="absolute w-full border-t" style={{ top: `${i * 35}px`, borderColor: "rgba(0,0,0,0.05)" }} />
          ))}
          {[...Array(8)].map((_, i) => (
            <div key={i} className="absolute h-full border-l" style={{ left: `${i * 48}px`, borderColor: "rgba(0,0,0,0.05)" }} />
          ))}
          {/* Roads */}
          <div className="absolute left-0 right-0 h-4 bg-white opacity-50" style={{ top: 80 }} />
          <div className="absolute left-0 right-0 h-3 bg-white opacity-40" style={{ top: 160 }} />
          <div className="absolute top-0 bottom-0 w-4 bg-white opacity-50" style={{ left: 96 }} />
          <div className="absolute top-0 bottom-0 w-3 bg-white opacity-40" style={{ right: 80 }} />
        </div>

        {/* Markers */}
        {[
          { x: 35, y: 35, status: "PENDING" as RequestStatus, ref: "REQ-341" },
          { x: 55, y: 55, status: "ASSIGNED" as RequestStatus, ref: "REQ-338" },
          { x: 70, y: 25, status: "IN_TRANSIT" as RequestStatus, ref: "REQ-335" },
          { x: 20, y: 65, status: "PENDING" as RequestStatus, ref: "REQ-339" },
          { x: 80, y: 60, status: "ASSIGNED" as RequestStatus, ref: "REQ-330" },
        ].map((m, i) => (
          <button
            key={i}
            onClick={() => setSelected(MOCK_REQUESTS[i % MOCK_REQUESTS.length])}
            className="absolute flex flex-col items-center transition-transform active:scale-90"
            style={{ left: `${m.x}%`, top: `${m.y}%`, transform: "translate(-50%, -100%)" }}
          >
            <div
              className="w-8 h-8 rounded-full flex items-center justify-center shadow-lg border-2 border-white"
              style={{ backgroundColor: m.status === "PENDING" ? "#F59E0B" : m.status === "ASSIGNED" ? "#3B82F6" : "#F97316" }}
            >
              <MapPin size={14} color="white" />
            </div>
            <div className="text-[9px] font-bold mt-0.5 bg-white px-1 rounded shadow" style={{ color: "#1A1C19" }}>{m.ref}</div>
          </button>
        ))}

        {/* Legend */}
        <div className="absolute bottom-3 left-3 bg-white rounded-xl p-2.5 shadow-md">
          {[
            { color: "#F59E0B", label: "Pending" },
            { color: "#3B82F6", label: "Assigned" },
            { color: "#F97316", label: "In Transit" },
          ].map((l) => (
            <div key={l.label} className="flex items-center gap-1.5 mb-1 last:mb-0">
              <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: l.color }} />
              <span className="text-[10px] text-foreground font-medium">{l.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Sidebar list */}
      <div className="bg-card border-t border-border" style={{ maxHeight: 220, overflowY: "auto" }}>
        <div className="px-4 pt-3 pb-1 flex items-center justify-between">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Active Queue</p>
          <span className="text-xs text-muted-foreground">3 requests</span>
        </div>
        {MOCK_REQUESTS.filter((r) => ["PENDING", "ASSIGNED", "IN_TRANSIT"].includes(r.status)).map((req) => (
          <button
            key={req.id}
            onClick={() => setSelected(req)}
            className="w-full flex items-center gap-3 px-4 py-3 border-t border-border text-left"
          >
            <div className="w-2 h-2 rounded-full" style={{ backgroundColor: statusConfig[req.status].color }} />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-mono text-muted-foreground">{req.requestRef}</p>
              <p className="text-sm font-medium text-foreground truncate">{categoryConfig[req.category].label("en")}</p>
            </div>
            <StatusBadge status={req.status} locale="en" />
          </button>
        ))}
      </div>

      {/* Request detail modal */}
      {selected && (
        <div className="absolute inset-0 bg-black/40 flex items-end z-10" onClick={() => setSelected(null)}>
          <div className="w-full bg-card rounded-t-3xl p-5 pb-6" onClick={(e) => e.stopPropagation()}>
            <div className="w-10 h-1 bg-muted rounded-full mx-auto mb-4" />
            <div className="flex items-center justify-between mb-3">
              <p className="text-base font-bold text-foreground">{selected.requestRef}</p>
              <StatusBadge status={selected.status} locale="en" />
            </div>
            <p className="text-sm text-muted-foreground mb-3">{selected.address}</p>
            <div className="grid grid-cols-2 gap-2 mb-4">
              <div className="bg-muted rounded-xl p-3">
                <p className="text-[10px] text-muted-foreground uppercase tracking-wide">Type</p>
                <p className="text-sm font-medium text-foreground mt-0.5">{categoryConfig[selected.category].label("en")}</p>
              </div>
              <div className="bg-muted rounded-xl p-3">
                <p className="text-[10px] text-muted-foreground uppercase tracking-wide">Volume</p>
                <p className="text-sm font-medium text-foreground mt-0.5">{volumeConfig[selected.volumeTier].label("en")}</p>
              </div>
            </div>

            {/* Assign unit */}
            <div className="bg-muted rounded-xl p-3 mb-4">
              <p className="text-[10px] text-muted-foreground uppercase tracking-wide mb-1.5">Assign Collection Unit</p>
              <select className="w-full bg-transparent text-sm text-foreground outline-none">
                <option>Select available unit…</option>
                <option>TRUCK-07 · Kariakoo Zone</option>
                <option>TRUCK-03 · Sinza Zone</option>
                <option>TRUCK-11 · Msasani Zone</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                className="py-3 rounded-xl text-sm font-medium border-2"
                style={{ borderColor: "#25D366", color: "#25D366" }}
              >
                <MessageCircle size={14} className="inline mr-1" />
                WhatsApp
              </button>
              <PrimaryButton label="Assign →" onClick={() => setSelected(null)} fullWidth />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function AdminUsersView() {
  const [search, setSearch] = useState("");
  const [users, setUsers] = useState(MOCK_ADMIN_USERS);

  return (
    <div className="flex flex-col h-full bg-background">
      <div className="bg-card border-b border-border px-4 pt-2 pb-3">
        <AndroidStatusBar />
        <h1 className="text-base font-semibold text-foreground mb-2">User Management</h1>
        <div className="flex items-center gap-2 bg-input-background rounded-xl px-3 py-2">
          <Search size={15} style={{ color: "#52634F" }} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search users…"
            className="flex-1 bg-transparent text-sm outline-none text-foreground placeholder:text-muted-foreground"
          />
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-2">
        {users.filter((u) => u.name.toLowerCase().includes(search.toLowerCase())).map((user) => (
          <div key={user.id} className="bg-card rounded-2xl p-4 border border-border">
            <div className="flex items-start gap-3">
              <div
                className="w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold shrink-0"
                style={{ backgroundColor: user.active ? "#1A6B38" : "#8DA88A" }}
              >
                {user.name[0]}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold text-foreground">{user.name}</p>
                  <span
                    className="text-[10px] font-bold px-2 py-0.5 rounded-full"
                    style={{ backgroundColor: user.active ? "#DCFCE7" : "#F3F4F6", color: user.active ? "#15803D" : "#6B7280" }}
                  >
                    {user.active ? "Active" : "Inactive"}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">{user.phone}</p>
                <div className="flex items-center gap-3 mt-1.5">
                  <span className="text-[10px] text-muted-foreground">{user.requests} requests</span>
                  <span className="text-[10px] text-muted-foreground">·</span>
                  <span className="text-[10px] text-muted-foreground">{user.lang === "sw" ? "🇹🇿 Kiswahili" : "🇬🇧 English"}</span>
                  <span className="text-[10px] text-muted-foreground">·</span>
                  <span className="text-[10px] text-muted-foreground">Joined {user.joined}</span>
                </div>
              </div>
            </div>
            <div className="flex gap-2 mt-3">
              <button
                className="flex-1 py-2 rounded-xl text-xs font-medium border border-border text-muted-foreground"
                style={{ backgroundColor: "#F4FAF4" }}
              >
                View Requests
              </button>
              <button
                onClick={() => setUsers((prev) => prev.map((u) => u.id === user.id ? { ...u, active: !u.active } : u))}
                className="flex-1 py-2 rounded-xl text-xs font-medium"
                style={{
                  backgroundColor: user.active ? "#FEE2E2",
                  color: user.active ? "#BA1A1A" : "#1A6B38",
                  border: `1px solid ${user.active ? "#FCA5A5" : "#86EFAC"}`,
                } as React.CSSProperties}
              >
                {user.active ? "Deactivate" : "Activate"}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function AdminAnalyticsView() {
  return (
    <div className="flex flex-col h-full bg-background">
      <div className="bg-card border-b border-border px-4 pt-2 pb-3">
        <AndroidStatusBar />
        <div className="flex items-center justify-between">
          <h1 className="text-base font-semibold text-foreground">Analytics</h1>
          <button
            className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full border border-border text-muted-foreground"
          >
            <Download size={12} />
            Export CSV
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-4">
        {/* KPI cards */}
        <div className="grid grid-cols-3 gap-3">
          {[
            { label: "Today", value: "31", delta: "+4", color: "#1A6B38" },
            { label: "This Week", value: "142", delta: "+18", color: "#1D4ED8" },
            { label: "Avg. Hrs", value: "3.2h", delta: "-0.4", color: "#15803D" },
          ].map((kpi) => (
            <div key={kpi.label} className="bg-card rounded-2xl p-3 border border-border text-center">
              <p className="text-xl font-bold" style={{ color: kpi.color }}>{kpi.value}</p>
              <p className="text-[10px] text-muted-foreground">{kpi.label}</p>
              <div className="flex items-center justify-center gap-0.5 mt-1">
                <TrendingUp size={9} style={{ color: kpi.color }} />
                <span className="text-[9px] font-medium" style={{ color: kpi.color }}>{kpi.delta}</span>
              </div>
            </div>
          ))}
        </div>

        {/* Status breakdown */}
        <div className="bg-card rounded-2xl border border-border p-4">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-3">Status Breakdown</p>
          {[
            { status: "PENDING" as RequestStatus, count: 8 },
            { status: "ASSIGNED" as RequestStatus, count: 5 },
            { status: "IN_TRANSIT" as RequestStatus, count: 3 },
            { status: "COLLECTED" as RequestStatus, count: 12 },
          ].map(({ status, count }) => {
            const cfg = statusConfig[status];
            const pct = Math.round((count / 31) * 100);
            return (
              <div key={status} className="mb-3 last:mb-0">
                <div className="flex items-center justify-between mb-1">
                  <div className="flex items-center gap-1.5">
                    <span style={{ color: cfg.color }}>{cfg.icon}</span>
                    <span className="text-xs font-medium text-foreground">{cfg.label("en")}</span>
                  </div>
                  <span className="text-xs font-mono text-muted-foreground">{count}</span>
                </div>
                <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                  <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: cfg.color }} />
                </div>
              </div>
            );
          })}
        </div>

        {/* Line chart */}
        <div className="bg-card rounded-2xl border border-border p-4">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-3">Daily Requests (7 days)</p>
          <ResponsiveContainer width="100%" height={130}>
            <LineChart data={analyticsLineData} margin={{ top: 4, right: 4, bottom: 0, left: -28 }}>
              <XAxis dataKey="day" tick={{ fontSize: 10, fill: "#52634F" }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 10, fill: "#52634F" }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={{ fontSize: 11, borderRadius: 8, border: "1px solid #DCE8D8" }} />
              <Line type="monotone" dataKey="requests" stroke="#1A6B38" strokeWidth={2.5} dot={{ fill: "#1A6B38", r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Pie chart */}
        <div className="bg-card rounded-2xl border border-border p-4">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-3">Requests by Category</p>
          <div className="flex items-center gap-4">
            <ResponsiveContainer width={120} height={120}>
              <PieChart>
                <Pie data={categoryPieData} cx="50%" cy="50%" innerRadius={30} outerRadius={55} dataKey="value" paddingAngle={2}>
                  {categoryPieData.map((entry, index) => (
                    <Cell key={index} fill={entry.color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="flex-1 flex flex-col gap-1.5">
              {categoryPieData.map((d) => (
                <div key={d.name} className="flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: d.color }} />
                  <span className="text-[10px] text-foreground flex-1">{d.name}</span>
                  <span className="text-[10px] font-mono text-muted-foreground">{d.value}%</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="h-2" />
      </div>
    </div>
  );
}

function AdminShell({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<AdminTab>("map");

  const tabs: { id: AdminTab; icon: React.ReactNode; label: string }[] = [
    { id: "map", icon: <MapPin size={22} />, label: "Live Map" },
    { id: "users", icon: <Users size={22} />, label: "Users" },
    { id: "analytics", icon: <BarChart3 size={22} />, label: "Analytics" },
  ];

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-hidden relative">
        {tab === "map" && <AdminMapView onTabChange={setTab} />}
        {tab === "users" && <AdminUsersView />}
        {tab === "analytics" && <AdminAnalyticsView />}
      </div>
      <div className="flex items-center justify-around bg-card border-t border-border pb-2 pt-1" style={{ borderTop: "2px solid #0F4524" }}>
        {tabs.map((t) => {
          const active = tab === t.id;
          return (
            <button key={t.id} onClick={() => setTab(t.id)} className="flex flex-col items-center gap-0.5 py-1 flex-1">
              <div
                className="flex items-center justify-center w-14 h-8 rounded-full transition-all"
                style={{ backgroundColor: active ? "#0F4524" : "transparent" }}
              >
                <span style={{ color: active ? "#FFFFFF" : "#52634F" }}>{t.icon}</span>
              </div>
              <span className="text-[10px] font-medium" style={{ color: active ? "#0F4524" : "#52634F" }}>{t.label}</span>
            </button>
          );
        })}
        <button onClick={onLogout} className="flex flex-col items-center gap-0.5 py-1 flex-1">
          <div className="flex items-center justify-center w-14 h-8 rounded-full">
            <LogOut size={22} style={{ color: "#BA1A1A" }} />
          </div>
          <span className="text-[10px] font-medium" style={{ color: "#BA1A1A" }}>Sign Out</span>
        </button>
      </div>
    </div>
  );
}

// ─── Root App ────────────────────────────────────────────────────────────────

export default function App() {
  const [screen, setScreen] = useState<AppScreen>("splash");
  const [locale, setLocale] = useState<Locale>("en");
  const [activeTab, setActiveTab] = useState<Tab>("home");
  const [selectedRequest, setSelectedRequest] = useState<WasteRequest | null>(null);

  // New request form state
  const [newCategory, setNewCategory] = useState<WasteCategory | null>(null);
  const [newVolume, setNewVolume] = useState<VolumeTier | null>(null);
  const [newAddress, setNewAddress] = useState("");
  const [newNotes, setNewNotes] = useState("");

  const activeReqCount = MOCK_REQUESTS.filter((r) => r.status === "PENDING" || r.status === "ASSIGNED").length;

  const goHome = () => {
    setActiveTab("home");
    setScreen("home");
  };

  const renderTabContent = () => {
    if (activeTab === "home") {
      return (
        <HomeScreen
          locale={locale}
          onNewRequest={() => setScreen("new-request-category")}
          onViewRequest={(r) => { setSelectedRequest(r); setScreen("request-detail"); }}
        />
      );
    }
    if (activeTab === "history") {
      return (
        <HistoryScreen
          locale={locale}
          onViewRequest={(r) => { setSelectedRequest(r); setScreen("request-detail"); }}
        />
      );
    }
    if (activeTab === "profile") {
      return (
        <ProfileScreen
          locale={locale}
          onLocaleChange={setLocale}
          onLogout={() => setScreen("login")}
        />
      );
    }
  };

  return (
    <div
      className="flex items-center justify-center min-h-screen"
      style={{ backgroundColor: "#1A1C19", fontFamily: "'Roboto', sans-serif" }}
    >
      {/* Phone shell */}
      <div
        className="relative flex flex-col overflow-hidden shadow-2xl"
        style={{
          width: 390,
          height: 844,
          borderRadius: 44,
          backgroundColor: "#F4FAF4",
          border: "8px solid #0a0a0a",
          boxShadow: "0 0 0 2px #2a2a2a, 0 40px 80px rgba(0,0,0,0.7), inset 0 0 0 1px rgba(255,255,255,0.05)",
        }}
      >
        {/* Notch */}
        <div
          className="absolute top-0 left-1/2 -translate-x-1/2 z-20"
          style={{
            width: 120,
            height: 28,
            backgroundColor: "#0a0a0a",
            borderRadius: "0 0 18px 18px",
          }}
        />

        {/* Screen content */}
        <div className="flex-1 flex flex-col overflow-hidden" style={{ paddingTop: 28 }}>
          {/* Splash */}
          {screen === "splash" && (
            <SplashScreen
              onContinue={() => setScreen("login")}
              onAdmin={() => setScreen("admin-login")}
            />
          )}

          {/* Auth */}
          {screen === "login" && (
            <LoginScreen
              locale={locale}
              onLogin={() => setScreen("home")}
              onRegister={() => setScreen("home")}
              onLocaleChange={setLocale}
            />
          )}

          {/* New Request Flow */}
          {screen === "new-request-category" && (
            <NewRequestCategory
              locale={locale}
              onBack={goHome}
              onNext={(cat) => { setNewCategory(cat); setScreen("new-request-volume"); }}
            />
          )}
          {screen === "new-request-volume" && newCategory && (
            <NewRequestVolume
              locale={locale}
              category={newCategory}
              onBack={() => setScreen("new-request-category")}
              onNext={(vol) => { setNewVolume(vol); setScreen("new-request-location"); }}
            />
          )}
          {screen === "new-request-location" && (
            <NewRequestLocation
              locale={locale}
              onBack={() => setScreen("new-request-volume")}
              onNext={(addr, notes) => { setNewAddress(addr); setNewNotes(notes); setScreen("new-request-confirm"); }}
            />
          )}
          {screen === "new-request-confirm" && newCategory && newVolume && (
            <NewRequestConfirm
              locale={locale}
              category={newCategory}
              volumeTier={newVolume}
              address={newAddress}
              notes={newNotes}
              onBack={() => setScreen("new-request-location")}
              onSubmit={() => setScreen("new-request-submitted")}
            />
          )}
          {screen === "new-request-submitted" && (
            <NewRequestSubmitted locale={locale} onDone={goHome} />
          )}

          {/* Request Detail */}
          {screen === "request-detail" && selectedRequest && (
            <RequestDetailScreen
              request={selectedRequest}
              locale={locale}
              onBack={() => setScreen("home")}
            />
          )}

          {/* Main app with tabs */}
          {screen === "home" && (
            <div className="flex flex-col h-full">
              <div className="flex-1 overflow-hidden">{renderTabContent()}</div>
              <BottomNav
                activeTab={activeTab}
                onTabChange={setActiveTab}
                locale={locale}
                badgeCount={activeReqCount}
              />
            </div>
          )}

          {/* Admin flow */}
          {screen === "admin-login" && (
            <AdminLogin
              onLogin={() => setScreen("admin-map")}
              onBack={() => setScreen("splash")}
            />
          )}
          {(screen === "admin-map" || screen === "admin-users" || screen === "admin-analytics") && (
            <AdminShell onLogout={() => setScreen("admin-login")} />
          )}
        </div>

        {/* Home indicator bar */}
        <div className="flex justify-center pb-2 pt-1 bg-card">
          <div className="w-28 h-1 rounded-full bg-foreground opacity-20" />
        </div>
      </div>

      {/* Desktop hint */}
      <div className="absolute bottom-6 text-center">
        <p className="text-xs" style={{ color: "rgba(255,255,255,0.3)" }}>
          WasteLink · East Africa Waste Collection Platform
        </p>
      </div>
    </div>
  );
}
