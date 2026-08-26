from flask import Flask, render_template, request, redirect, url_for, session, flash, jsonify
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import text
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, date
import pickle, numpy as np, os
import re
 
app = Flask(__name__)
app.secret_key = os.getenv('SMARTHEALTH_SECRET_KEY', 'smarthealth_secret_2024')
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///healthcare.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)
 
# ─── Load ML models ───────────────────────────────────────────────────────────
BASE = os.path.dirname(__file__)
model    = pickle.load(open(os.path.join(BASE,'models','best_model.pkl'),'rb'))
encoder  = pickle.load(open(os.path.join(BASE,'models','disease_encoder.pkl'),'rb'))
symptoms = pickle.load(open(os.path.join(BASE,'models','symptoms.pkl'),'rb'))
 
DISEASE_INFO = {
    'Flu':              {'specialist':'General Physician','precautions':'Rest, fluids, avoid crowds','severity':'Moderate'},
    'Common Cold':      {'specialist':'General Physician','precautions':'Stay warm, drink fluids, rest','severity':'Mild'},
    'Malaria':          {'specialist':'Infectious Disease','precautions':'Mosquito nets, antimalarial meds','severity':'High'},
    'Dengue':           {'specialist':'Infectious Disease','precautions':'Hydration, avoid NSAIDs, rest','severity':'High'},
    'Typhoid':          {'specialist':'Gastroenterologist','precautions':'Clean water, hygiene, antibiotics','severity':'High'},
    'Diabetes':         {'specialist':'Endocrinologist','precautions':'Diet control, exercise, monitor sugar','severity':'Chronic'},
    'Hypertension':     {'specialist':'Cardiologist','precautions':'Low-salt diet, exercise, stress management','severity':'Chronic'},
    'Pneumonia':        {'specialist':'Pulmonologist','precautions':'Antibiotics, rest, hydration','severity':'High'},
    'Asthma':           {'specialist':'Pulmonologist','precautions':'Avoid triggers, carry inhaler','severity':'Moderate'},
    'COVID-19':         {'specialist':'General Physician','precautions':'Isolation, hydration, oxygen monitoring','severity':'High'},
    'Migraine':         {'specialist':'Neurologist','precautions':'Avoid triggers, dark room, pain relief','severity':'Moderate'},
    'Gastritis':        {'specialist':'Gastroenterologist','precautions':'Bland diet, antacids, avoid spicy food','severity':'Moderate'},
    'Anemia':           {'specialist':'Hematologist','precautions':'Iron-rich diet, supplements','severity':'Moderate'},
    'Arthritis':        {'specialist':'Rheumatologist','precautions':'Physical therapy, anti-inflammatory meds','severity':'Chronic'},
    'Jaundice':         {'specialist':'Hepatologist','precautions':'Rest, hydration, avoid alcohol','severity':'High'},
}
 
# ─── Database Models ───────────────────────────────────────────────────────────
class User(db.Model):
    id         = db.Column(db.Integer, primary_key=True)
    name       = db.Column(db.String(100), nullable=False)
    email      = db.Column(db.String(120), unique=True, nullable=False)
    password   = db.Column(db.String(200), nullable=False)
    age        = db.Column(db.Integer)
    gender     = db.Column(db.String(10))
    phone      = db.Column(db.String(15))
    blood_group= db.Column(db.String(5))
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
 
 
class Doctor(db.Model):
    id          = db.Column(db.Integer, primary_key=True)
    name        = db.Column(db.String(100), nullable=False)
    email       = db.Column(db.String(120), unique=True, nullable=False)
    password    = db.Column(db.String(200), nullable=False)
    department  = db.Column(db.String(100))
    phone       = db.Column(db.String(15))
    created_at  = db.Column(db.DateTime, default=datetime.utcnow)
 
 
class Prediction(db.Model):
    id          = db.Column(db.Integer, primary_key=True)
    user_id     = db.Column(db.Integer, db.ForeignKey('user.id'))
    disease     = db.Column(db.String(100))
    confidence  = db.Column(db.Float)
    symptoms    = db.Column(db.String(500))
    severity    = db.Column(db.String(20))
    specialist  = db.Column(db.String(100))
    created_at  = db.Column(db.DateTime, default=datetime.utcnow)
 
 
class Appointment(db.Model):
    id          = db.Column(db.Integer, primary_key=True)
    user_id     = db.Column(db.Integer, db.ForeignKey('user.id'))
    doctor_id   = db.Column(db.Integer, db.ForeignKey('doctor.id'))   # NEW: links to actual doctor account
    doctor_name = db.Column(db.String(100))   # kept for backward-compat / display
    department  = db.Column(db.String(100))
    date        = db.Column(db.String(20))
    time        = db.Column(db.String(20))
    reason      = db.Column(db.String(300))
    # Scheduled -> Pending doctor action, Accepted, Rejected, Cancelled, Completed
    status      = db.Column(db.String(20), default='Pending')
    doctor_note = db.Column(db.String(300))          # optional note doctor leaves on accept/reject
    created_at  = db.Column(db.DateTime, default=datetime.utcnow)
    responded_at= db.Column(db.DateTime)
 
    patient  = db.relationship('User', backref='appointments')
    doctor   = db.relationship('Doctor', backref='appointments')
 
 
class Notification(db.Model):
    """Notification sent to a doctor when a patient books an appointment,
    and back to the patient when the doctor responds."""
    id             = db.Column(db.Integer, primary_key=True)
    doctor_id      = db.Column(db.Integer, db.ForeignKey('doctor.id'))
    user_id        = db.Column(db.Integer, db.ForeignKey('user.id'))
    appointment_id = db.Column(db.Integer, db.ForeignKey('appointment.id'))
    message        = db.Column(db.String(300))
    is_read        = db.Column(db.Boolean, default=False)
    created_at     = db.Column(db.DateTime, default=datetime.utcnow)
 
 
# ─── Database migration for existing SQLite databases ─────────────────────────
def migrate_existing_database():
    """Keep existing patient data and add columns/tables introduced in newer versions."""
    # Create any completely new tables first (Doctor, Notification).
    db.create_all()

    result = db.session.execute(text("PRAGMA table_info(appointment)"))
    existing = {row[1] for row in result}

    missing_columns = {
        "doctor_id": "INTEGER",
        "doctor_note": "VARCHAR(300)",
        "responded_at": "DATETIME",
    }

    for column, column_type in missing_columns.items():
        if column not in existing:
            db.session.execute(text(f"ALTER TABLE appointment ADD COLUMN {column} {column_type}"))

    db.session.commit()


# ─── Auth helpers ──────────────────────────────────────────────────────────────
def login_required(f):
    from functools import wraps
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            flash('Please login first.', 'warning')
            return redirect(url_for('login'))
        return f(*args, **kwargs)
    return decorated
 
 
def doctor_login_required(f):
    from functools import wraps
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'doctor_id' not in session:
            flash('Please login as a doctor first.', 'warning')
            return redirect(url_for('doctor_login'))
        return f(*args, **kwargs)
    return decorated
 
 
# ─── Patient Routes ─────────────────────────────────────────────────────────────
@app.route('/')
def index():
    return render_template('index.html')
 
@app.route('/register', methods=['GET','POST'])
def register():
    if request.method == 'POST':
        name  = request.form['name']
        email = request.form['email']
        pwd   = request.form['password']
        age   = request.form.get('age',0)
        gender= request.form.get('gender','')
        phone = request.form.get('phone','')
        bg    = request.form.get('blood_group','')
        if User.query.filter_by(email=email).first():
            flash('Email already registered!','danger')
            return redirect(url_for('register'))
        user = User(name=name, email=email,
                    password=generate_password_hash(pwd),
                    age=age, gender=gender, phone=phone, blood_group=bg)
        db.session.add(user); db.session.commit()
        flash('Registration successful! Please login.','success')
        return redirect(url_for('login'))
    return render_template('register.html')
 
@app.route('/login', methods=['GET','POST'])
def login():
    if request.method == 'POST':
        email = request.form['email']
        pwd   = request.form['password']
        user  = User.query.filter_by(email=email).first()
        if user and check_password_hash(user.password, pwd):
            session['user_id'] = user.id
            session['user_name'] = user.name
            flash(f'Welcome back, {user.name}!','success')
            return redirect(url_for('dashboard'))
        flash('Invalid credentials!','danger')
    return render_template('login.html')
 
@app.route('/logout')
def logout():
    session.clear()
    flash('Logged out successfully.','info')
    return redirect(url_for('login'))
 
@app.route('/dashboard')
@login_required
def dashboard():
    user = User.query.get(session['user_id'])
    preds = Prediction.query.filter_by(user_id=user.id).order_by(Prediction.created_at.desc()).limit(5).all()
    apts  = Appointment.query.filter_by(user_id=user.id).order_by(Appointment.created_at.desc()).limit(5).all()
    total_preds = Prediction.query.filter_by(user_id=user.id).count()
    total_apts  = Appointment.query.filter_by(user_id=user.id).count()
    return render_template('dashboard.html', user=user, predictions=preds,
                           appointments=apts, total_preds=total_preds, total_apts=total_apts)
 
@app.route('/prediction', methods=['GET','POST'])
@login_required
def prediction():
    result = None
    if request.method == 'POST':
        selected = request.form.getlist('symptoms')
        vec = [1 if s in selected else 0 for s in symptoms]
        probs = model.predict_proba([vec])[0]
        top_idx = np.argsort(probs)[::-1][:3]
        top_disease = encoder.classes_[top_idx[0]]
        confidence  = round(probs[top_idx[0]] * 100, 1)
        info = DISEASE_INFO.get(top_disease, {'specialist':'General Physician','precautions':'Consult a doctor','severity':'Unknown'})
        top3 = [(encoder.classes_[i], round(probs[i]*100,1)) for i in top_idx]
        pred = Prediction(user_id=session['user_id'], disease=top_disease,
                          confidence=confidence, symptoms=', '.join(selected),
                          severity=info['severity'], specialist=info['specialist'])
        db.session.add(pred); db.session.commit()
        result = {'disease': top_disease, 'confidence': confidence,
                  'top3': top3, **info, 'symptoms_selected': selected}
    return render_template('prediction.html', symptoms=symptoms, result=result)
 
 
# ─── Appointment Routes (Patient side) ──────────────────────────────────────────
@app.route('/appointment', methods=['GET','POST'])
@login_required
def appointment():
    if request.method == 'POST':
        doctor_id = request.form.get('doctor_id')
        doctor = Doctor.query.get(doctor_id) if doctor_id else None
 
        apt = Appointment(
            user_id    = session['user_id'],
            doctor_id  = doctor.id if doctor else None,
            doctor_name= doctor.name if doctor else request.form.get('doctor_name',''),
            department = doctor.department if doctor else request.form.get('department',''),
            date       = request.form['date'],
            time       = request.form['time'],
            reason     = request.form['reason'],
            status     = 'Pending'
        )
        db.session.add(apt)
        db.session.commit()
 
        # NEW: notify the doctor about the new booking request
        if doctor:
            patient = User.query.get(session['user_id'])
            note = Notification(
                doctor_id=doctor.id,
                user_id=patient.id,
                appointment_id=apt.id,
                message=f'New appointment request from {patient.name} on {apt.date} at {apt.time}.'
            )
            db.session.add(note)
            db.session.commit()
            flash('Appointment request sent to the doctor. Waiting for confirmation.', 'success')
        else:
            flash('Appointment booked, but no doctor account was linked to send a notification.', 'warning')
 
        return redirect(url_for('appointment'))
 
    apts = Appointment.query.filter_by(user_id=session['user_id']).order_by(Appointment.created_at.desc()).all()
    doctors = Doctor.query.order_by(Doctor.name).all()
    return render_template('appointment.html', appointments=apts, doctors=doctors)
 
@app.route('/appointment/cancel/<int:apt_id>')
@login_required
def cancel_appointment(apt_id):
    apt = Appointment.query.get_or_404(apt_id)
    if apt.user_id == session['user_id']:
        apt.status = 'Cancelled'
        db.session.commit()
        flash('Appointment cancelled.','info')
    return redirect(url_for('appointment'))
 
@app.route('/reports')
@login_required
def reports():
    user  = User.query.get(session['user_id'])
    preds = Prediction.query.filter_by(user_id=user.id).order_by(Prediction.created_at.desc()).all()
    apts  = Appointment.query.filter_by(user_id=user.id).order_by(Appointment.created_at.desc()).all()
    disease_counts = {}
    for p in preds:
        disease_counts[p.disease] = disease_counts.get(p.disease, 0) + 1
    return render_template('reports.html', user=user, predictions=preds,
                           appointments=apts, disease_counts=disease_counts)
 

 
@app.route('/api/patient/notifications')
@login_required
def patient_notifications():
    """Patient polls this to see if the doctor accepted/rejected their appointment."""
    notes = Notification.query.filter_by(user_id=session['user_id'], is_read=False)\
                               .order_by(Notification.created_at.desc()).all()
    data = [{'id': n.id, 'message': n.message, 'created_at': n.created_at.strftime('%d %b, %I:%M %p')} for n in notes]
    return jsonify({'notifications': data})
 
@app.route('/api/patient/notifications/<int:note_id>/read')
@login_required
def mark_patient_notification_read(note_id):
    note = Notification.query.get_or_404(note_id)
    if note.user_id == session['user_id']:
        note.is_read = True
        db.session.commit()
    return jsonify({'ok': True})
 
 
# ─── Doctor Routes ───────────────────────────────────────────────────────────────
@app.route('/doctor/register', methods=['GET','POST'])
def doctor_register():
    if request.method == 'POST':
        name  = request.form['name']
        email = request.form['email']
        pwd   = request.form['password']
        dept  = request.form.get('department','')
        phone = request.form.get('phone','')
        if Doctor.query.filter_by(email=email).first():
            flash('Email already registered!','danger')
            return redirect(url_for('doctor_register'))
        doc = Doctor(name=name, email=email, password=generate_password_hash(pwd),
                     department=dept, phone=phone)
        db.session.add(doc); db.session.commit()
        flash('Doctor account created! Please login.','success')
        return redirect(url_for('doctor_login'))
    return render_template('doctor_register.html')
 
@app.route('/doctor/login', methods=['GET','POST'])
def doctor_login():
    if request.method == 'POST':
        email = request.form['email']
        pwd   = request.form['password']
        doc   = Doctor.query.filter_by(email=email).first()
        if doc and check_password_hash(doc.password, pwd):
            session['doctor_id'] = doc.id
            session['doctor_name'] = doc.name
            flash(f'Welcome, Dr. {doc.name}!','success')
            return redirect(url_for('doctor_dashboard'))
        flash('Invalid credentials!','danger')
    return render_template('doctor_login.html')
 
@app.route('/doctor/logout')
def doctor_logout():
    session.pop('doctor_id', None)
    session.pop('doctor_name', None)
    flash('Logged out successfully.','info')
    return redirect(url_for('doctor_login'))
 
@app.route('/doctor/dashboard')
@doctor_login_required
def doctor_dashboard():
    doc = Doctor.query.get(session['doctor_id'])
    pending = Appointment.query.filter_by(doctor_id=doc.id, status='Pending')\
                                .order_by(Appointment.created_at.desc()).all()
    upcoming = Appointment.query.filter_by(doctor_id=doc.id, status='Accepted')\
                                 .order_by(Appointment.date.asc()).all()
    history = Appointment.query.filter(Appointment.doctor_id==doc.id,
                                        Appointment.status.in_(['Rejected','Cancelled','Completed']))\
                                .order_by(Appointment.created_at.desc()).limit(10).all()
    unread_count = Notification.query.filter_by(doctor_id=doc.id, is_read=False).count()
    return render_template('doctor_dashboard.html', doctor=doc, pending=pending,
                           upcoming=upcoming, history=history, unread_count=unread_count)
 
@app.route('/api/doctor/notifications')
@doctor_login_required
def doctor_notifications():
    """Doctor polls this for live 'new appointment request' alerts."""
    notes = Notification.query.filter_by(doctor_id=session['doctor_id'], is_read=False)\
                               .order_by(Notification.created_at.desc()).all()
    data = [{'id': n.id, 'message': n.message, 'appointment_id': n.appointment_id,
             'created_at': n.created_at.strftime('%d %b, %I:%M %p')} for n in notes]
    return jsonify({'notifications': data, 'count': len(data)})
 
@app.route('/doctor/appointment/<int:apt_id>/accept', methods=['POST'])
@doctor_login_required
def accept_appointment(apt_id):
    apt = Appointment.query.get_or_404(apt_id)
    if apt.doctor_id != session['doctor_id']:
        flash('Not authorized for this appointment.','danger')
        return redirect(url_for('doctor_dashboard'))
 
    apt.status = 'Accepted'
    apt.doctor_note = request.form.get('note','')
    apt.responded_at = datetime.utcnow()
    db.session.commit()
 
    # notify the patient back
    note = Notification(
        doctor_id=apt.doctor_id, user_id=apt.user_id, appointment_id=apt.id,
        message=f'Dr. {session["doctor_name"]} accepted your appointment on {apt.date} at {apt.time}.'
    )
    db.session.add(note); db.session.commit()
 
    flash('Appointment accepted.','success')
    return redirect(url_for('doctor_dashboard'))
 
@app.route('/doctor/appointment/<int:apt_id>/reject', methods=['POST'])
@doctor_login_required
def reject_appointment(apt_id):
    apt = Appointment.query.get_or_404(apt_id)
    if apt.doctor_id != session['doctor_id']:
        flash('Not authorized for this appointment.','danger')
        return redirect(url_for('doctor_dashboard'))
 
    apt.status = 'Rejected'
    apt.doctor_note = request.form.get('note','')
    apt.responded_at = datetime.utcnow()
    db.session.commit()
 
    note = Notification(
        doctor_id=apt.doctor_id, user_id=apt.user_id, appointment_id=apt.id,
        message=f'Dr. {session["doctor_name"]} could not accept your appointment on {apt.date} at {apt.time}.'
                + (f' Reason: {apt.doctor_note}' if apt.doctor_note else '')
    )
    db.session.add(note); db.session.commit()
 
    flash('Appointment rejected.','info')
    return redirect(url_for('doctor_dashboard'))
 
@app.route('/doctor/appointment/<int:apt_id>/complete', methods=['POST'])
@doctor_login_required
def complete_appointment(apt_id):
    apt = Appointment.query.get_or_404(apt_id)
    if apt.doctor_id == session['doctor_id']:
        apt.status = 'Completed'
        db.session.commit()
        flash('Marked as completed.','success')
    return redirect(url_for('doctor_dashboard'))
 
 

# ─── Mobile / Android REST API ────────────────────────────────────────────────
# These endpoints are intentionally separate from the existing browser/session
# routes above. Android authenticates with a signed Bearer token.
from itsdangerous import URLSafeTimedSerializer, BadSignature, SignatureExpired

try:
    from flask_cors import CORS
    CORS(app, resources={r"/api/*": {"origins": "*"}})
except ImportError:
    # The dependency is listed in requirements.txt. The web UI still works if
    # someone starts the server before installing it.
    pass

TOKEN_MAX_AGE = 60 * 60 * 24 * 7  # 7 days
token_serializer = URLSafeTimedSerializer(app.secret_key, salt="smarthealth-mobile-v1")


def create_api_token(user_id):
    return token_serializer.dumps({"user_id": int(user_id)})


def get_api_user():
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    token = header.split(" ", 1)[1].strip()
    if not token:
        return None
    try:
        data = token_serializer.loads(token, max_age=TOKEN_MAX_AGE)
        return User.query.get(int(data["user_id"]))
    except (BadSignature, SignatureExpired, TypeError, ValueError, KeyError):
        return None


def api_user_required():
    user = get_api_user()
    if user is None:
        return None, (jsonify({"success": False, "message": "Authentication required."}), 401)
    return user, None


DOCTOR_TOKEN_MAX_AGE = 60 * 60 * 24 * 7
doctor_token_serializer = URLSafeTimedSerializer(
    app.secret_key, salt="smarthealth-doctor-mobile-v1"
)

def create_doctor_api_token(doctor_id):
    return doctor_token_serializer.dumps({"doctor_id": int(doctor_id)})

def get_api_doctor():
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    token = header.split(" ", 1)[1].strip()
    if not token:
        return None
    try:
        data = doctor_token_serializer.loads(token, max_age=DOCTOR_TOKEN_MAX_AGE)
        return Doctor.query.get(int(data["doctor_id"]))
    except (BadSignature, SignatureExpired, TypeError, ValueError, KeyError):
        return None

def api_doctor_required():
    doctor = get_api_doctor()
    if doctor is None:
        return None, (jsonify({"success": False, "message": "Doctor authentication required."}), 401)
    return doctor, None

def serialize_doctor(doctor):
    return {
        "id": doctor.id,
        "name": doctor.name,
        "email": doctor.email,
        "department": doctor.department,
        "phone": doctor.phone,
    }

def serialize_doctor_appointment(apt):
    patient = apt.patient
    return {
        "id": apt.id,
        "patient_id": apt.user_id,
        "patient_name": patient.name if patient else "",
        "patient_email": patient.email if patient else "",
        "patient_phone": patient.phone if patient else None,
        "patient_age": patient.age if patient else None,
        "patient_gender": patient.gender if patient else None,
        "patient_blood_group": patient.blood_group if patient else None,
        "date": apt.date or "",
        "time": apt.time or "",
        "reason": apt.reason or "",
        "status": apt.status or "",
        "doctor_note": apt.doctor_note,
        "created_at": apt.created_at.isoformat() if apt.created_at else None,
    }

def serialize_user(user):
    return {
        "id": user.id,
        "name": user.name,
        "email": user.email,
        "age": user.age,
        "gender": user.gender,
        "phone": user.phone,
        "blood_group": user.blood_group,
    }


def serialize_prediction(pred):
    return {
        "id": pred.id,
        "disease": pred.disease,
        "confidence": pred.confidence,
        "symptoms": [s.strip() for s in (pred.symptoms or "").split(",") if s.strip()],
        "severity": pred.severity,
        "specialist": pred.specialist,
        "created_at": pred.created_at.isoformat() if pred.created_at else None,
    }


def serialize_appointment(apt):
    return {
        "id": apt.id,
        "doctor_id": apt.doctor_id,
        "doctor_name": apt.doctor_name,
        "department": apt.department,
        "date": apt.date,
        "time": apt.time,
        "reason": apt.reason,
        "status": apt.status,
        "doctor_note": apt.doctor_note,
        "created_at": apt.created_at.isoformat() if apt.created_at else None,
    }


@app.route('/api/doctor/login', methods=['POST'])
def api_doctor_login():
    data = request.get_json(silent=True) or {}

    # Get login data
    email = str(data.get("email", ""))
    password = str(data.get("password", ""))

    # Clean email - remove invisible/unwanted characters
    email = re.sub(
        r"[^a-zA-Z0-9._%+\-@]",
        "",
        email
    )

    email = email.strip().lower()

    print("========== DOCTOR API LOGIN ==========")
    print("Email received:", repr(email))
    print("Email length:", len(email))

    # Get all doctors
    doctors = Doctor.query.all()

    doctor = None

    print("ALL DOCTOR EMAILS:")

    for d in doctors:

        db_email = str(d.email)

        # Clean database email also
        db_email = re.sub(
            r"[^a-zA-Z0-9._%+\-@]",
            "",
            db_email
        )

        db_email = db_email.strip().lower()

        print(
            "DB EMAIL:",
            repr(db_email),
            "| Length:",
            len(db_email),
            "| Match:",
            db_email == email
        )

        if db_email == email:
            doctor = d
            break

    print(
        "Matched doctor:",
        doctor.email if doctor else None
    )

    # Doctor not found
    if not doctor:
        print("❌ Doctor not found.")

        return jsonify({
            "success": False,
            "message": "Invalid doctor email."
        }), 401

    # Check password
    password_matches = check_password_hash(
        doctor.password,
        password
    )

    print("Doctor ID:", doctor.id)
    print("Doctor name:", doctor.name)
    print("Password hash exists:", bool(doctor.password))
    print("Password matches:", password_matches)

    # Wrong password
    if not password_matches:
        print("❌ Invalid doctor password.")

        return jsonify({
            "success": False,
            "message": "Invalid doctor password."
        }), 401

    # Create authentication token
    token = create_doctor_api_token(doctor.id)

    print("✅ Doctor login successful!")
    print("Token created:", bool(token))

    return jsonify({
        "success": True,
        "message": f"Welcome, Dr. {doctor.name}.",
        "doctor": serialize_doctor(doctor),
        "token": token
    }), 200

@app.route('/api/doctor/profile', methods=['GET'])
def api_doctor_profile():
    doctor, error = api_doctor_required()
    if error:
        return error
    return jsonify({"success": True, "doctor": serialize_doctor(doctor)})

@app.route('/api/mobile/doctor/appointments', methods=['GET'])
def api_mobile_doctor_appointments():
    doctor, error = api_doctor_required()
    if error:
        return error

    pending = Appointment.query.filter_by(
        doctor_id=doctor.id, status="Pending"
    ).order_by(Appointment.created_at.desc()).all()

    upcoming = Appointment.query.filter_by(
        doctor_id=doctor.id, status="Accepted"
    ).order_by(Appointment.date.asc(), Appointment.time.asc()).all()

    history = Appointment.query.filter(
        Appointment.doctor_id == doctor.id,
        Appointment.status.in_(["Rejected", "Cancelled", "Completed"])
    ).order_by(Appointment.created_at.desc()).limit(30).all()

    return jsonify({
        "success": True,
        "data": {
            "pending": [serialize_doctor_appointment(a) for a in pending],
            "upcoming": [serialize_doctor_appointment(a) for a in upcoming],
            "history": [serialize_doctor_appointment(a) for a in history],
        }
    })

@app.route('/api/mobile/doctor/notifications', methods=['GET'])
def api_mobile_doctor_notifications():
    doctor, error = api_doctor_required()
    if error:
        return error

    notes = Notification.query.filter_by(
        doctor_id=doctor.id, is_read=False
    ).order_by(Notification.created_at.desc()).all()

    return jsonify({
        "success": True,
        "notifications": [
            {
                "id": n.id,
                "message": n.message,
                "appointment_id": n.appointment_id,
                "created_at": n.created_at.strftime("%d %b, %I:%M %p")
            }
            for n in notes
        ],
        "count": len(notes)
    })

def _doctor_action_json(apt_id, action):
    doctor, error = api_doctor_required()
    if error:
        return error

    apt = Appointment.query.get_or_404(apt_id)
    if apt.doctor_id != doctor.id:
        return jsonify({"success": False, "message": "Not authorized for this appointment."}), 403

    data = request.get_json(silent=True) or {}
    note = str(data.get("note", "")).strip()

    if action == "accept":
        apt.status = "Accepted"
        message = f"Dr. {doctor.name} accepted your appointment on {apt.date} at {apt.time}."
    elif action == "reject":
        apt.status = "Rejected"
        message = f"Dr. {doctor.name} could not accept your appointment on {apt.date} at {apt.time}."
        if note:
            message += f" Reason: {note}"
    else:
        apt.status = "Completed"
        message = f"Dr. {doctor.name} marked your appointment on {apt.date} at {apt.time} as completed."

    apt.doctor_note = note
    apt.responded_at = datetime.utcnow()

    notification = Notification(
        doctor_id=apt.doctor_id,
        user_id=apt.user_id,
        appointment_id=apt.id,
        message=message
    )
    db.session.add(notification)
    db.session.commit()

    return jsonify({
        "success": True,
        "message": f"Appointment {action}d successfully." if action in ("accept", "reject") else "Appointment completed successfully."
    })

@app.route('/api/mobile/doctor/appointments/<int:apt_id>/accept', methods=['POST'])
def api_mobile_doctor_accept(apt_id):
    return _doctor_action_json(apt_id, "accept")

@app.route('/api/mobile/doctor/appointments/<int:apt_id>/reject', methods=['POST'])
def api_mobile_doctor_reject(apt_id):
    return _doctor_action_json(apt_id, "reject")

@app.route('/api/mobile/doctor/appointments/<int:apt_id>/complete', methods=['POST'])
def api_mobile_doctor_complete(apt_id):
    return _doctor_action_json(apt_id, "complete")

@app.route('/api/health', methods=['GET'])
def api_health():
    return jsonify({
        "success": True,
        "service": "SmartHealthcare API",
        "status": "online",
        "version": "1.0.0"
    })


@app.route('/api/register', methods=['POST'])
def api_register():
    data = request.get_json(silent=True) or {}
    name = str(data.get("name", "")).strip()
    email = str(data.get("email", "")).strip().lower()
    password = str(data.get("password", ""))
    age = data.get("age")
    gender = str(data.get("gender", "")).strip()
    phone = str(data.get("phone", "")).strip()
    blood_group = str(data.get("blood_group", "")).strip()

    if not name or not email or not password:
        return jsonify({"success": False, "message": "Name, email and password are required."}), 400
    if len(password) < 6:
        return jsonify({"success": False, "message": "Password must contain at least 6 characters."}), 400
    if User.query.filter_by(email=email).first():
        return jsonify({"success": False, "message": "Email is already registered."}), 409

    try:
        age_value = int(age) if age not in (None, "", "null") else None
    except (TypeError, ValueError):
        age_value = None

    user = User(
        name=name,
        email=email,
        password=generate_password_hash(password),
        age=age_value,
        gender=gender,
        phone=phone,
        blood_group=blood_group,
    )
    db.session.add(user)
    db.session.commit()

    return jsonify({
        "success": True,
        "message": "Registration successful.",
        "user": serialize_user(user),
        "token": create_api_token(user.id)
    }), 201


@app.route('/api/login', methods=['POST'])
def api_login():
    data = request.get_json(silent=True) or {}
    email = str(data.get("email", "")).strip().lower()
    password = str(data.get("password", ""))

    user = User.query.filter_by(email=email).first()
    if not user or not check_password_hash(user.password, password):
        return jsonify({"success": False, "message": "Invalid email or password."}), 401

    return jsonify({
        "success": True,
        "message": f"Welcome back, {user.name}.",
        "user": serialize_user(user),
        "token": create_api_token(user.id)
    })


@app.route('/api/profile', methods=['GET'])
def api_profile():
    user, error = api_user_required()
    if error:
        return error
    return jsonify({"success": True, "user": serialize_user(user)})


@app.route('/api/predict', methods=['POST'])
def api_predict():
    user, error = api_user_required()
    if error:
        return error

    data = request.get_json(silent=True) or {}
    selected = data.get("symptoms", [])

    if isinstance(selected, str):
        selected = [s.strip() for s in selected.split(",") if s.strip()]
    if not isinstance(selected, list):
        return jsonify({"success": False, "message": "symptoms must be an array."}), 400

    # Keep only model-supported symptoms and remove duplicates.
    selected = list(dict.fromkeys(str(s).strip().lower() for s in selected))
    selected = [s for s in selected if s in symptoms]

    if not selected:
        return jsonify({"success": False, "message": "Please select at least one valid symptom."}), 400

    vec = [1 if s in selected else 0 for s in symptoms]
    probs = model.predict_proba([vec])[0]
    top_idx = np.argsort(probs)[::-1][:3]
    top_disease = str(encoder.classes_[top_idx[0]])
    confidence = round(float(probs[top_idx[0]]) * 100, 1)

    info = DISEASE_INFO.get(
        top_disease,
        {
            "specialist": "General Physician",
            "precautions": "Consult a qualified doctor.",
            "severity": "Unknown",
        },
    )
    top3 = [
        {
            "disease": str(encoder.classes_[i]),
            "confidence": round(float(probs[i]) * 100, 1),
        }
        for i in top_idx
    ]

    pred = Prediction(
        user_id=user.id,
        disease=top_disease,
        confidence=confidence,
        symptoms=", ".join(selected),
        severity=info["severity"],
        specialist=info["specialist"],
    )
    db.session.add(pred)
    db.session.commit()

    return jsonify({
        "success": True,
        "prediction": {
            "disease": top_disease,
            "confidence": confidence,
            "severity": info["severity"],
            "specialist": info["specialist"],
            "precautions": info["precautions"],
            "top3": top3,
            "symptoms_selected": selected,
            "disclaimer": "This is an ML-based prediction, not a medical diagnosis."
        }
    })


@app.route('/api/symptoms', methods=['GET'])
def api_symptoms():
    return jsonify({"success": True, "symptoms": list(symptoms)})


@app.route('/api/history', methods=['GET'])
def api_history():
    user, error = api_user_required()
    if error:
        return error

    limit = request.args.get("limit", 50, type=int)
    limit = max(1, min(limit, 100))
    predictions = (
        Prediction.query.filter_by(user_id=user.id)
        .order_by(Prediction.created_at.desc())
        .limit(limit)
        .all()
    )
    return jsonify({
        "success": True,
        "predictions": [serialize_prediction(p) for p in predictions]
    })


@app.route('/api/stats', methods=['GET'])
def api_mobile_stats():
    user, error = api_user_required()
    if error:
        return error

    predictions = Prediction.query.filter_by(user_id=user.id).all()
    counts = {}
    for p in predictions:
        counts[p.disease] = counts.get(p.disease, 0) + 1

    appointments = Appointment.query.filter_by(user_id=user.id).count()
    return jsonify({
        "success": True,
        "total_predictions": len(predictions),
        "total_appointments": appointments,
        "disease_counts": counts
    })


@app.route('/api/doctors', methods=['GET'])
def api_doctors():
    doctors = Doctor.query.order_by(Doctor.name.asc()).all()
    return jsonify({
        "success": True,
        "doctors": [
            {
                "id": d.id,
                "name": d.name,
                "email": d.email,
                "department": d.department,
                "phone": d.phone,
            }
            for d in doctors
        ]
    })


@app.route('/api/appointments', methods=['GET', 'POST'])
def api_appointments():
    user, error = api_user_required()
    if error:
        return error

    if request.method == 'GET':
        appointments = (
            Appointment.query.filter_by(user_id=user.id)
            .order_by(Appointment.created_at.desc())
            .all()
        )
        return jsonify({
            "success": True,
            "appointments": [serialize_appointment(a) for a in appointments]
        })

    data = request.get_json(silent=True) or {}
    doctor_id = data.get("doctor_id")
    date_value = str(data.get("date", "")).strip()
    time_value = str(data.get("time", "")).strip()
    reason = str(data.get("reason", "")).strip()

    if not doctor_id or not date_value or not time_value or not reason:
        return jsonify({
            "success": False,
            "message": "Doctor, date, time and reason are required."
        }), 400

    doctor = Doctor.query.get(doctor_id)
    if not doctor:
        return jsonify({"success": False, "message": "Doctor not found."}), 404

    apt = Appointment(
        user_id=user.id,
        doctor_id=doctor.id,
        doctor_name=doctor.name,
        department=doctor.department,
        date=date_value,
        time=time_value,
        reason=reason,
        status="Pending",
    )
    db.session.add(apt)
    db.session.commit()

    note = Notification(
        doctor_id=doctor.id,
        user_id=user.id,
        appointment_id=apt.id,
        message=f"New appointment request from {user.name} on {date_value} at {time_value}."
    )
    db.session.add(note)
    db.session.commit()

    return jsonify({
        "success": True,
        "message": "Appointment request sent to the doctor.",
        "appointment": serialize_appointment(apt)
    }), 201


@app.route('/api/appointments/<int:apt_id>/cancel', methods=['POST'])
def api_cancel_appointment(apt_id):
    user, error = api_user_required()
    if error:
        return error

    apt = Appointment.query.get_or_404(apt_id)
    if apt.user_id != user.id:
        return jsonify({"success": False, "message": "Not authorized."}), 403
    if apt.status not in ("Pending", "Accepted"):
        return jsonify({"success": False, "message": "This appointment cannot be cancelled."}), 400

    apt.status = "Cancelled"
    db.session.commit()
    return jsonify({"success": True, "message": "Appointment cancelled."})


@app.route('/api/notifications', methods=['GET'])
def api_notifications():
    user, error = api_user_required()
    if error:
        return error

    notes = (
        Notification.query.filter_by(user_id=user.id, is_read=False)
        .order_by(Notification.created_at.desc())
        .all()
    )
    return jsonify({
        "success": True,
        "notifications": [
            {
                "id": n.id,
                "message": n.message,
                "appointment_id": n.appointment_id,
                "created_at": n.created_at.isoformat() if n.created_at else None,
            }
            for n in notes
        ]
    })


@app.route('/api/notifications/<int:note_id>/read', methods=['POST'])
def api_mark_notification_read(note_id):
    user, error = api_user_required()
    if error:
        return error

    note = Notification.query.get_or_404(note_id)
    if note.user_id != user.id:
        return jsonify({"success": False, "message": "Not authorized."}), 403
    note.is_read = True
    db.session.commit()
    return jsonify({"success": True})


def chatbot_reply(message):
    """Small offline health assistant: no API key is required.

    It gives general health information and routes urgent red-flag messages to
    emergency care. It deliberately does not claim to diagnose a patient.
    """
    text = str(message or "").strip().lower()

    if not text:
        return "Please tell me what you are experiencing, for example: fever, cough, headache, or stomach pain."

    emergency_terms = [
        "can't breathe", "cannot breathe", "difficulty breathing",
        "severe chest pain", "chest pain", "unconscious", "fainted",
        "stroke", "seizure", "heavy bleeding", "vomiting blood",
        "suicidal", "self harm"
    ]
    if any(term in text for term in emergency_terms):
        return (
            "This may be urgent. Please seek immediate medical attention or "
            "contact your local emergency service. Do not rely on this chatbot "
            "for emergency care."
        )

    responses = {
        "fever": "For fever, rest, drink enough fluids, and monitor your temperature. If it is high, persistent, or accompanied by severe symptoms, consult a doctor.",
        "cough": "For a cough, stay hydrated and avoid smoke or other irritants. A persistent, worsening, or breathing-related cough should be evaluated by a doctor.",
        "headache": "For a mild headache, rest, hydrate, and reduce screen or bright-light exposure. Frequent or severe headaches need medical evaluation.",
        "stomach": "For stomach discomfort, choose light meals and stay hydrated. Severe pain, repeated vomiting, blood in stool, or worsening symptoms require medical care.",
        "vomiting": "Take small, frequent sips of water or oral rehydration fluid. Repeated vomiting, dehydration, blood, or severe abdominal pain needs medical attention.",
        "diarrhea": "Hydration is important with diarrhea. Consider oral rehydration fluid and light foods. Seek care for blood, severe dehydration, high fever, or persistent symptoms.",
        "cold": "For common cold symptoms, rest, fluids, and avoiding irritants can help. Seek medical advice if symptoms are severe or do not improve.",
        "stress": "For stress, try slow breathing, regular sleep, hydration, and a short walk if comfortable. If stress feels overwhelming or persistent, talk with a qualified professional.",
    }
    for keyword, reply in responses.items():
        if keyword in text:
            return reply

    return (
        "I can provide general health information, but I cannot confirm a diagnosis. "
        "Tell me your main symptom, how long you have had it, and whether it is getting "
        "better or worse. For diagnosis or treatment, please consult a qualified doctor."
    )


@app.route('/api/chat', methods=['POST'])
def api_chat():
    user, error = api_user_required()
    if error:
        return error

    data = request.get_json(silent=True) or {}
    message = str(data.get("message", "")).strip()
    if not message:
        return jsonify({"success": False, "message": "Message is required."}), 400

    reply = chatbot_reply(message)
    return jsonify({
        "success": True,
        "reply": reply,
        "disclaimer": "General health information only; not a medical diagnosis."
    })


if __name__ == '__main__':
    with app.app_context():

        # Initialize / migrate database
        migrate_existing_database()

        # -----------------------------------------
        # CREATE DEMO DOCTORS IF THEY DON'T EXIST
        # -----------------------------------------
        if Doctor.query.count() == 0:
            demo_docs = [
                Doctor(
                    name="Dr. Ravi Sharma",
                    email="ravi.sharma@smarthealth.com",
                    password=generate_password_hash("doctor123"),
                    department="General Physician",
                    phone="9000000001"
                ),
                Doctor(
                    name="Dr. Neha Kapoor",
                    email="neha.kapoor@smarthealth.com",
                    password=generate_password_hash("doctor123"),
                    department="Cardiologist",
                    phone="9000000002"
                )
            ]

            db.session.add_all(demo_docs)
            db.session.commit()

            print("✅ Demo doctors created.")

        # -----------------------------------------
        # RESET DEMO DOCTOR PASSWORDS
        # -----------------------------------------

        ravi = Doctor.query.filter_by(
            email="ravi.sharma@smarthealth.com"
        ).first()

        neha = Doctor.query.filter_by(
            email="neha.kapoor@smarthealth.com"
        ).first()

        if ravi:
            ravi.password = generate_password_hash("doctor123")
            print("✅ Ravi password reset")

        if neha:
            neha.password = generate_password_hash("doctor123")
            print("✅ Neha password reset")

        db.session.commit()

        # -----------------------------------------
        # VERIFY PASSWORD
        # -----------------------------------------

        if ravi:
            print("--------------------------------")
            print("DOCTOR LOGIN TEST")
            print("Email:", ravi.email)
            print("Password: doctor123")
            print(
                "Password check:",
                check_password_hash(
                    ravi.password,
                    "doctor123"
                )
            )
            print("--------------------------------")

        print("✅ Database initialized!")

    # -----------------------------------------
    # START FLASK SERVER
    # -----------------------------------------

    app.run(
        host="0.0.0.0",
        debug=True,
        port=5000
    )