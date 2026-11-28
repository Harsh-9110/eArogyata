// Generated from docs/openapi.json. Run npm run generate:api; do not edit.
export interface Schemas {
"User": { "id": number; "name": string; "email": string; "role": "PATIENT" | "DOCTOR" | "ADMIN" | "WARD_STAFF" };
"Credentials": { "email": string; "password": string };
"Register": { "name": string; "email": string; "password": string };
"CreateUser": { "name": string; "email": string; "password": string; "role": "PATIENT" | "DOCTOR" | "ADMIN" | "WARD_STAFF"; "specialty"?: string | null; "room"?: string | null };
"AuthResponse": { "accessToken": string; "expiresIn": number; "user": Schemas["User"] };
"Doctor": { "id": number; "userId": number; "name": string; "specialty": string; "room": string };
"Session": { "id": number; "doctorId": number; "doctorName": string; "specialty": string; "room": string; "date": string; "averageMinutes": number; "state": "OPEN" | "CLOSED"; "waiting": number; "booked": number; "servingTicket": number | null };
"CreateSession": { "doctorId": number; "date": string; "averageMinutes": number; "startTime": string; "slotMinutes": number; "slotCount": number };
"Slot": { "id": number; "sessionId": number; "startsAt": string; "available": boolean };
"BookAppointment": { "slotId": number };
"Appointment": { "id": number; "sessionId": number; "slotId": number; "patientId": number; "patientName": string; "doctorName": string; "specialty": string; "date": string; "startsAt": string; "ticketNumber": number; "status": "BOOKED" | "WAITING" | "IN_CONSULTATION" | "COMPLETED" | "CANCELLED" | "NO_SHOW"; "position": number | null; "estimatedWaitMinutes": number | null };
"Ward": { "id": number; "name": string; "floor": string };
"Bed": { "id": number; "wardId": number; "wardName": string; "floor": string; "label": string; "status": "AVAILABLE" | "RESERVED" | "OCCUPIED" | "CLEANING" | "OUT_OF_SERVICE" };
"ReserveBed": { "bedId": number; "patientId"?: number | null };
"Reservation": { "id": number; "bedId": number; "bedLabel": string; "wardName": string; "patientId": number; "patientName": string; "status": "RESERVED" | "ADMITTED" | "DISCHARGED" | "CANCELLED"; "reservedAt": string };
"BedStatus": { "status": "AVAILABLE" | "CLEANING" | "OUT_OF_SERVICE" };
"AuditEvent": { "id": number; "actorId": number | null; "action": string; "resourceType": string; "resourceId": string; "outcome": string; "occurredAt": string };
"Problem": { "type": string; "title": string; "status": number; "detail": string; "instance"?: string };
"Invalidation": { "resource": "queue" | "beds" | "heartbeat" };
}
export interface Operations {
register: {input: {body: Schemas["Register"]}; response: Schemas["AuthResponse"]};
login: {input: {body: Schemas["Credentials"]}; response: Schemas["AuthResponse"]};
refresh: {input: {}; response: Schemas["AuthResponse"]};
logout: {input: {}; response: void};
me: {input: {}; response: Schemas["User"]};
createUser: {input: {body: Schemas["CreateUser"]}; response: Schemas["User"]};
listUsers: {input: {}; response: Array<Schemas["User"]>};
listAudit: {input: {query?: {before?: number}}; response: Array<Schemas["AuditEvent"]>};
doctors: {input: {}; response: Array<Schemas["Doctor"]>};
sessions: {input: {query?: {date?: string}}; response: Array<Schemas["Session"]>};
createSession: {input: {body: Schemas["CreateSession"]}; response: Schemas["Session"]};
slots: {input: {path: {id: number}}; response: Array<Schemas["Slot"]>};
queue: {input: {path: {id: number}}; response: Array<Schemas["Appointment"]>};
next: {input: {path: {id: number}}; response: Schemas["Appointment"]};
bookAppointment: {input: {body: Schemas["BookAppointment"]}; response: Schemas["Appointment"]};
appointments: {input: {}; response: Array<Schemas["Appointment"]>};
checkIn: {input: {path: {id: number}}; response: Schemas["Appointment"]};
cancelAppointment: {input: {path: {id: number}}; response: Schemas["Appointment"]};
complete: {input: {path: {id: number}}; response: Schemas["Appointment"]};
wards: {input: {}; response: Array<Schemas["Ward"]>};
beds: {input: {}; response: Array<Schemas["Bed"]>};
bedStatus: {input: {body: Schemas["BedStatus"];path: {id: number}}; response: Schemas["Bed"]};
reservations: {input: {}; response: Array<Schemas["Reservation"]>};
reserve: {input: {body: Schemas["ReserveBed"]}; response: Schemas["Reservation"]};
admit: {input: {path: {id: number}}; response: Schemas["Reservation"]};
cancelReservation: {input: {path: {id: number}}; response: Schemas["Reservation"]};
discharge: {input: {path: {id: number}}; response: Schemas["Reservation"]};
}
export const operationMeta = {
  "register": {
    "method": "POST",
    "path": "/auth/register"
  },
  "login": {
    "method": "POST",
    "path": "/auth/login"
  },
  "refresh": {
    "method": "POST",
    "path": "/auth/refresh"
  },
  "logout": {
    "method": "POST",
    "path": "/auth/logout"
  },
  "me": {
    "method": "GET",
    "path": "/auth/me"
  },
  "createUser": {
    "method": "POST",
    "path": "/admin/users"
  },
  "listUsers": {
    "method": "GET",
    "path": "/admin/users"
  },
  "listAudit": {
    "method": "GET",
    "path": "/admin/audit"
  },
  "doctors": {
    "method": "GET",
    "path": "/doctors"
  },
  "sessions": {
    "method": "GET",
    "path": "/sessions"
  },
  "createSession": {
    "method": "POST",
    "path": "/sessions"
  },
  "slots": {
    "method": "GET",
    "path": "/sessions/{id}/slots"
  },
  "queue": {
    "method": "GET",
    "path": "/sessions/{id}/queue"
  },
  "next": {
    "method": "POST",
    "path": "/sessions/{id}/next"
  },
  "bookAppointment": {
    "method": "POST",
    "path": "/appointments"
  },
  "appointments": {
    "method": "GET",
    "path": "/appointments"
  },
  "checkIn": {
    "method": "POST",
    "path": "/appointments/{id}/check-in"
  },
  "cancelAppointment": {
    "method": "POST",
    "path": "/appointments/{id}/cancel"
  },
  "complete": {
    "method": "POST",
    "path": "/appointments/{id}/complete"
  },
  "wards": {
    "method": "GET",
    "path": "/wards"
  },
  "beds": {
    "method": "GET",
    "path": "/beds"
  },
  "bedStatus": {
    "method": "PATCH",
    "path": "/beds/{id}/status"
  },
  "reservations": {
    "method": "GET",
    "path": "/reservations"
  },
  "reserve": {
    "method": "POST",
    "path": "/reservations"
  },
  "admit": {
    "method": "POST",
    "path": "/reservations/{id}/admit"
  },
  "cancelReservation": {
    "method": "POST",
    "path": "/reservations/{id}/cancel"
  },
  "discharge": {
    "method": "POST",
    "path": "/reservations/{id}/discharge"
  }
} as const;
