import numpy as np
import pickle
import os
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder

# 15 diseases with corresponding symptom patterns
diseases = [
    'Flu', 'Common Cold', 'Malaria', 'Dengue', 'Typhoid',
    'Diabetes', 'Hypertension', 'Pneumonia', 'Asthma', 'COVID-19',
    'Migraine', 'Gastritis', 'Anemia', 'Arthritis', 'Jaundice'
]

# Symptoms used as features
symptoms = [
    'fever', 'cough', 'headache', 'fatigue', 'body_pain',
    'nausea', 'vomiting', 'diarrhea', 'chest_pain', 'shortness_of_breath',
    'rash', 'sweating', 'chills', 'loss_of_appetite', 'joint_pain'
]

np.random.seed(42)

# Disease symptom patterns (rough mapping)
patterns = {
    'Flu':                [1,1,1,1,1, 0,0,0,0,0, 0,1,1,1,0],
    'Common Cold':        [1,1,0,0,0, 0,0,0,0,0, 0,0,0,0,0],
    'Malaria':            [1,0,1,1,1, 1,1,0,0,0, 0,1,1,1,0],
    'Dengue':             [1,0,1,1,1, 1,1,0,0,0, 1,0,0,1,1],
    'Typhoid':            [1,0,1,1,1, 1,1,1,0,0, 0,1,0,1,0],
    'Diabetes':           [0,0,1,1,0, 0,0,0,0,0, 0,1,0,1,1],
    'Hypertension':       [0,0,1,1,0, 0,0,0,1,1, 0,1,0,0,1],
    'Pneumonia':          [1,1,1,1,1, 0,0,0,1,1, 0,1,1,1,0],
    'Asthma':             [0,1,0,1,0, 0,0,0,1,1, 0,0,0,0,0],
    'COVID-19':           [1,1,1,1,1, 0,0,0,1,1, 0,0,1,1,0],
    'Migraine':           [0,0,1,1,0, 1,1,0,0,0, 0,0,0,0,0],
    'Gastritis':          [0,0,1,1,0, 1,1,1,1,0, 0,0,0,1,0],
    'Anemia':             [0,0,1,1,0, 0,0,0,0,1, 0,1,0,1,0],
    'Arthritis':          [0,0,1,1,1, 0,0,0,1,0, 0,0,0,0,1],
    'Jaundice':           [1,0,1,1,0, 1,1,0,0,0, 1,0,0,1,0],
}

X, y = [], []
for disease, pattern in patterns.items():
    for _ in range(200):
        noise = np.array(pattern) + np.random.choice([0,0,0,1,-1], size=15)
        noise = np.clip(noise, 0, 1)
        X.append(noise)
        y.append(disease)

X = np.array(X)
y = np.array(y)

le = LabelEncoder()
y_enc = le.fit_transform(y)

clf = RandomForestClassifier(n_estimators=100, random_state=42)
clf.fit(X, y_enc)

BASE = os.path.dirname(os.path.abspath(__file__))

with open(os.path.join(BASE, 'models', 'best_model.pkl'), 'wb') as f:
    pickle.dump(clf, f)
with open(os.path.join(BASE, 'models', 'disease_encoder.pkl'), 'wb') as f:
    pickle.dump(le, f)
with open(os.path.join(BASE, 'models', 'symptoms.pkl'), 'wb') as f:
    pickle.dump(symptoms, f)

print("Models saved successfully!")
print("Classes:", le.classes_)
