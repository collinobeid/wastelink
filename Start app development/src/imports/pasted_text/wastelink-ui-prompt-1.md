# WasteLink: God-Tier UI/UX Design Prompt

**Instructions for the User:** 
*Copy the text below and paste it directly into an AI UI generator (like v0.dev, Galileo AI, or Uizard), or give it directly to your Figma designer to generate the complete high-fidelity UI for WasteLink.*

***

### **Context & Vision**
Design a complete, God-Tier UI/UX for a modern waste management platform called **WasteLink**. The app must look and feel like a premium, billion-dollar logistics company (think Uber, Bolt, or Amazon Logistics) tailored specifically for waste collection. 

**Design Aesthetic & Vibe:**
*   **Theme:** Sleek, modern, and trustworthy. Use a striking contrast of "Eco-Green" (Vibrant Emerald) and "Deep Asphalt" (Dark Charcoal/Black) for dark mode. Light mode should feel clean, airy, and clinical with subtle off-white backgrounds.
*   **Style:** Glassmorphism for floating cards, soft drop shadows, heavily rounded corners (border-radius: 16px to 24px), and large, legible typography (use 'Inter' or 'Plus Jakarta Sans').
*   **Micro-interactions:** It should feel *alive*. Buttons should have clear active states, loading indicators should be fluid (e.g., Lottie-style truck driving animations), and map elements should feel highly integrated.
*   **Zero-Friction:** Every core action must be reachable within 2 taps.

Please design the following three separate environments:

---

### **1. Resident App (Mobile UI)**
*The user-facing mobile app for requesting waste pickups.*

**Screen A: The Home / Request Screen (The "Uber" Map)**
*   **Visual:** Full-screen Google Map interface centered on the user's location.
*   **Overlays:** 
    *   A sleek top search bar (floating) saying "Where's the waste located?".
    *   A bottom sheet (draggable) that houses the "Request Pickup" UI.
*   **Bottom Sheet Content:**
    *   **Category Selector:** Horizontal scrolling pill-shaped buttons with emojis (♻️ Recyclable, ☢️ Hazardous, 🗑️ General Waste).
    *   **Volume Slider:** A premium, custom slider ranging from "1 Bag" to "Full Truck".
    *   **"Find My Gate" Feature:** A small, aesthetic camera thumbnail button prompting the user to snap a photo of their gate/landmark for the driver.
    *   **Action Button:** A massive, vibrant Eco-Green "Confirm Pickup" button spanning the width of the screen.

**Screen B: Live Tracking (En-Route State)**
*   **Visual:** Map view dominating the screen. A 3D isometric truck icon is shown on the map, facing the direction of the route. A pulsing green route line connects the truck to the user's pin.
*   **Overlays:**
    *   Floating ETA pill (e.g., "Arriving in 4 mins").
    *   Bottom card showing the Driver's profile (photo, name, rating ⭐️ 4.9, and license plate number).
    *   Two floating action buttons: "Call Driver" and "WhatsApp".

---

### **2. Collector App (Fleet Driver Mobile UI)**
*The driver-facing app optimized for high visibility, daylight driving, and rapid interactions.*

**Screen A: The Dashboard & Radar**
*   **Visual:** High-contrast, easy-to-read "Radar" map looking for nearby pickups.
*   **Overlays:**
    *   A massive, glowing "GO ONLINE" toggle at the top.
    *   Floating cards popping up with new requests showing: Distance (e.g., 2.1 km), Volume, and Estimated Payout. 
    *   A large "Accept" (Green) and "Decline" (Red border) button on the request card.

**Screen B: Active Mission (Navigation & Verification)**
*   **Visual:** Turn-by-turn navigation map view.
*   **Overlays:**
    *   Top banner showing next turn instructions.
    *   Bottom sheet detailing the client's location and their "Gate Photo" thumbnail.
    *   **Action Sequence:** When the driver arrives, the bottom sheet expands. They see a "Verify Collection" button. Tapping it opens a quick camera overlay to snap the "Proof of Collection" (PoC) photo.
    *   **Swipe to Complete:** A large "Swipe to finish job" slider at the bottom to prevent accidental clicks.

---

### **3. God-Mode Admin Dashboard (Web/Desktop UI)**
*The control center for operators to oversee the entire city.*

**Screen A: The Command Center (Main Dashboard)**
*   **Layout:** Sidebar navigation on the left, top-level analytics cards, and a massive interactive map dominating the right side.
*   **Visual Elements:**
    *   **Dark Mode Preferred:** Looks like a modern command center.
    *   **Left Panel:** A live feed (like a stock ticker) of incoming requests, color-coded by status (Yellow = Pending, Green = En-Route, Blue = Completed).
    *   **Analytics Cards:** Clean graphs showing "Total Trucks Active", "Average Collection Time", and "Tons Collected Today".
    *   **The Map:** Shows clusters of waste requests (heatmaps) and live tracking dots of all active fleet trucks moving in real-time. Clicking a truck opens a beautiful side-panel showing the driver's current payload and route.

**Components to Include in the Design System:**
*   Floating Action Buttons (FABs)
*   Sleek Bottom Sheets with drag handles
*   Custom Map Markers (User pins, Truck pins, Dumpsite pins)
*   Skeleton loaders for when data is fetching
*   Success state animations (e.g., checkmarks, confetti for completed missions)
