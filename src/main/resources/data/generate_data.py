#!/usr/bin/env python3
"""
generate_data.py — Génération des 5 sources NexCore Technologies.
Distributions et corrélations inspirées du dataset IBM HR Analytics (Kaggle).
Usage  : python generate_data.py   (depuis src/main/resources/data/)
Deps   : pip install pandas numpy faker openpyxl
"""

import os
import random
import sqlite3
from datetime import date, timedelta

import numpy as np
import pandas as pd
from faker import Faker

# ── Configuration ──────────────────────────────────────────────────────────────
SEED           = 42
N_EMPLOYEES    = 1500
ATTRITION_BASE = 0.22   # taux d'attrition de référence (~IBM HR Analytics)
START_ID       = 200001
YEAR_MAX       = 2024
DATA_YEAR_MIN  = 2018   # début des données Timesheet / Formations / Evaluations

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))

random.seed(SEED)
np.random.seed(SEED)
fake = Faker(['fr_FR', 'de_DE', 'en_US'])
Faker.seed(SEED)

# ── Départements ───────────────────────────────────────────────────────────────
DEPT_CODES   = ['RD',  'IT',          'SALES', 'OPS',          'HR',              'ADMIN',           'MGMT']
DEPT_WEIGHTS = [0.20,   0.25,           0.20,   0.15,            0.08,              0.07,              0.05]

# Noms de départements attendus par chaque loader (DEPT_MAP dans le code Java)
DEPT_TO_FORMATION = {
    'RD': 'R&D', 'IT': 'IT', 'SALES': 'Sales',
    'OPS': 'Operations', 'HR': 'HR_Dept',
    'ADMIN': 'Admin', 'MGMT': 'Management',
}
DEPT_TO_TIMESHEET = {
    'RD': 'R&D', 'IT': 'IT_Systems', 'SALES': 'Sales',
    'OPS': 'Operations', 'HR': 'Human_Resources',
    'ADMIN': 'Administration', 'MGMT': 'Management',
}

SITES_BY_DEPT = {
    'RD':    ['Paris', 'Berlin'],
    'IT':    ['Paris', 'London', 'Amsterdam'],
    'SALES': ['Paris', 'Lyon', 'London'],
    'OPS':   ['Lyon', 'Berlin', 'Amsterdam'],
    'HR':    ['Paris', 'Lyon'],
    'ADMIN': ['Paris', 'Lyon', 'Berlin'],
    'MGMT':  ['Paris', 'London'],
}

# ── Postes, fourchettes salariales annuelles (USD) et niveaux ──────────────────
JOB_TITLES = {
    'RD': [
        ('Data Scientist',      65000, 118000),
        ('ML Engineer',         70000, 130000),
        ('Software Engineer',   58000, 108000),
        ('Research Analyst',    50000,  90000),
        ('R&D Manager',         85000, 142000),
        ('R&D Director',       100000, 168000),
    ],
    'IT': [
        ('IT Analyst',          47000,  86000),
        ('System Administrator',50000,  92000),
        ('Network Engineer',    54000, 100000),
        ('Security Engineer',   64000, 116000),
        ('IT Manager',          80000, 132000),
        ('IT Director',         95000, 160000),
    ],
    'SALES': [
        ('Sales Representative',40000,  72000),
        ('Account Manager',     50000,  95000),
        ('Business Developer',  54000, 100000),
        ('Sales Manager',       74000, 126000),
        ('Sales Director',      90000, 154000),
    ],
    'OPS': [
        ('Coordinator',         35000,  62000),
        ('Operations Analyst',  40000,  72000),
        ('Process Engineer',    54000,  96000),
        ('Supply Chain Manager',65000, 112000),
        ('Operations Manager',  70000, 124000),
    ],
    'HR': [
        ('Recruiter',           37000,  66000),
        ('HR Specialist',       40000,  70000),
        ('HR Business Partner', 54000,  96000),
        ('HR Manager',          65000, 112000),
        ('HR Director',         85000, 142000),
    ],
    'ADMIN': [
        ('Receptionist',        28000,  48000),
        ('Administrative Assistant', 30000, 54000),
        ('Executive Assistant', 40000,  68000),
        ('Office Manager',      45000,  78000),
    ],
    'MGMT': [
        ('Team Lead',           75000, 130000),
        ('Director',            95000, 164000),
        ('VP',                 120000, 208000),
        ('Executive',          135000, 232000),
    ],
}

TERM_REASONS = ['Resignation', 'Layoff', 'Retirement', 'Contract End', 'Mutual Agreement']
TERM_WEIGHTS = [0.50, 0.25, 0.10, 0.08, 0.07]

TRAINING_CATALOG = [
    ('Agile Scrum',                    2,  800, 1600),
    ('Cloud Architecture AWS',         4, 2000, 3800),
    ('Python Advanced',                6, 1900, 3200),
    ('Machine Learning Fundamentals',  5, 2400, 4200),
    ('Leadership & Management',        3, 1500, 2800),
    ('SQL & Data Analysis',            2,  750, 1600),
    ('Project Management PMP',         5, 2800, 5200),
    ('Cybersecurity Fundamentals',     3, 1400, 2600),
    ('Excel & Power Query',            1,  380,  820),
    ('Communication Skills',           2,  580, 1250),
    ('Digital Marketing',              3,  950, 2050),
    ('Financial Analysis',             4, 1900, 3600),
    ('DevOps & CI/CD',                 4, 2400, 4100),
    ('Change Management',              2,  980, 1950),
    ('React & JavaScript',             5, 2100, 3900),
    ('Power BI & Data Visualization',  3, 1150, 2350),
    ('HR Management',                  3, 1450, 2650),
    ('Supply Chain Optimization',      4, 1950, 3600),
    ('Negotiation Skills',             2,  880, 1750),
    ('Safety & Compliance',            1,  480, 1050),
    ('Six Sigma Green Belt',           5, 3000, 5500),
    ('Emotional Intelligence',         1,  500,  980),
    ('Tableau & Analytics',            3, 1200, 2400),
    ('Docker & Kubernetes',            4, 2200, 4000),
]

# ── Helpers ────────────────────────────────────────────────────────────────────

def rand_date(start: date, end: date) -> date:
    delta = max(0, (end - start).days)
    return start + timedelta(days=random.randint(0, delta))

def fmt(d) -> str:
    """dd/MM/yyyy — format attendu par ETLUtils (RH_Paie, JobHistory)."""
    return d.strftime('%d/%m/%Y') if d else ''

def fmt_iso(d) -> str:
    """YYYY-MM-DD — format attendu pour Formations_Learning."""
    return d.strftime('%Y-%m-%d') if d else ''


# ══════════════════════════════════════════════════════════════════════════════
# 1. POOL D'EMPLOYÉS — base commune à toutes les sources
# ══════════════════════════════════════════════════════════════════════════════

employees = []

for i in range(N_EMPLOYEES):
    emp_id = START_ID + i
    dept   = random.choices(DEPT_CODES, weights=DEPT_WEIGHTS)[0]
    titles = JOB_TITLES[dept]
    title, sal_min, sal_max = random.choice(titles)
    site   = random.choice(SITES_BY_DEPT[dept])
    gender = random.choices(['M', 'F'], weights=[0.59, 0.41])[0]

    first = fake.first_name_female() if gender == 'F' else fake.first_name_male()
    last  = fake.last_name()

    age = random.randint(23, 58)
    dob = date(YEAR_MAX - age, random.randint(1, 12), random.randint(1, 28))

    max_tenure = min(age - 22, 13)
    tenure_y   = random.randint(1, max(1, max_tenure))
    hire_year  = YEAR_MAX - tenure_y
    hire_date  = date(hire_year, random.randint(1, 12), random.randint(1, 28))

    # Traits intrinsèques (performance & satisfaction corrélés — IBM HR Analytics)
    perf = random.choices([1, 2, 3, 4], weights=[0.08, 0.32, 0.38, 0.22])[0]
    if perf >= 3:
        satisf = random.choices([1, 2, 3, 4], weights=[0.04, 0.18, 0.46, 0.32])[0]
    else:
        satisf = random.choices([1, 2, 3, 4], weights=[0.28, 0.42, 0.22, 0.08])[0]

    # Probabilité d'attrition (multi-facteurs, inspirée IBM HR Analytics)
    p = ATTRITION_BASE
    if satisf == 1: p += 0.32
    elif satisf == 2: p += 0.14
    elif satisf == 4: p -= 0.10
    if perf == 1: p += 0.14
    elif perf == 4: p -= 0.08
    if tenure_y <= 2: p += 0.12
    if age < 30: p += 0.08
    if dept == 'SALES': p += 0.04   # turnover naturellement plus élevé
    p = max(0.03, min(0.88, p))

    attrition = random.random() < p
    term_date = term_reason = None
    status    = 'Active'

    if attrition:
        min_t = hire_date + timedelta(days=180)
        max_t = date(YEAR_MAX, 12, 31)
        if min_t < max_t:
            term_date   = rand_date(min_t, max_t)
            term_reason = random.choices(TERM_REASONS, weights=TERM_WEIGHTS)[0]
            if age >= 55: term_reason = 'Retirement'
            elif perf == 1 and term_reason == 'Resignation':
                term_reason = random.choices(['Layoff', 'Resignation'], weights=[0.6, 0.4])[0]
            status = 'Terminated'
        else:
            attrition = False

    # Léger écart salarial H/F (~4-7 %) pour refléter une réalité observable
    sal_raw = random.uniform(sal_min * (1 + min(tenure_y * 0.018, 0.20)), sal_max)
    if gender == 'F':
        sal_raw *= random.uniform(0.93, 0.97)
    salary = int(sal_raw)

    employees.append({
        'emp_id':      emp_id,
        'first':       first,
        'last':        last,
        'dob':         dob,
        'gender':      gender,
        'dept':        dept,
        'title':       title,
        'titles_list': [t[0] for t in titles],
        'salary':      salary,
        'hire_date':   hire_date,
        'term_date':   term_date,
        'status':      status,
        'term_reason': term_reason or '',
        'satisf':      satisf,
        'perf':        perf,
        'site':        site,
        'tenure_y':    tenure_y,
        'attrition':   attrition,
        'age':         age,
    })


# ══════════════════════════════════════════════════════════════════════════════
# 2. RH_PAIE.CSV
# ══════════════════════════════════════════════════════════════════════════════

rh_rows = [{
    'EmployeeID':           e['emp_id'],
    'FirstName':            e['first'],
    'LastName':             e['last'],
    'DateOfBirth':          fmt(e['dob']),
    'Gender':               e['gender'],
    'DepartmentCode':       e['dept'],
    'JobTitle':             e['title'],
    'AnnualSalary':         e['salary'],
    'HireDate':             fmt(e['hire_date']),
    'TerminationDate':      fmt(e['term_date']),
    'EmploymentStatus':     e['status'],
    'TermReason':           e['term_reason'],
    'EmployeeSatisfaction': e['satisf'],
    'PerformanceRating':    e['perf'],
    'Site':                 e['site'],
} for e in employees]

pd.DataFrame(rh_rows).to_csv(
    os.path.join(OUTPUT_DIR, 'RH_Paie.csv'),
    index=False, encoding='utf-8-sig',
)
print(f"[RH_Paie]      {len(rh_rows):>5} employés  "
      f"({sum(1 for e in employees if e['attrition'])} départs, "
      f"{sum(1 for e in employees if not e['attrition'])} actifs)")


# ══════════════════════════════════════════════════════════════════════════════
# 3. JOBHISTORY.CSV
# ══════════════════════════════════════════════════════════════════════════════

jh_rows = []

for e in employees:
    end      = e['term_date'] or date(YEAR_MAX, 12, 31)
    sal_init = int(e['salary'] * random.uniform(0.78, 0.92))

    # Embauche initiale
    jh_rows.append({
        'EmployeeID':    e['emp_id'],
        'ChangeDate':    fmt(e['hire_date']),
        'FromJobTitle':  '',
        'ToJobTitle':    e['title'],
        'SalaryBefore':  '',
        'SalaryAfter':   sal_init,
        'ChangeType':    'Hire',
        'DepartmentCode': e['dept'],
        'Site':          e['site'],
    })

    # Nombre d'événements de carrière selon ancienneté + performance
    n = 0
    if e['tenure_y'] >= 2: n = 1
    if e['tenure_y'] >= 4: n = random.randint(1, 2)
    if e['tenure_y'] >= 7: n = random.randint(2, 3)
    if e['tenure_y'] >= 10: n = random.randint(2, 4)
    if e['perf'] == 4: n = min(n + 1, 5)
    if e['perf'] == 1: n = max(n - 1, 0)

    cur_date, cur_sal, cur_title = e['hire_date'], sal_init, e['title']

    for _ in range(n):
        remaining = (end - cur_date).days
        if remaining < 365:
            break
        next_d = cur_date + timedelta(days=random.randint(365, max(366, remaining - 30)))
        if next_d >= end:
            break

        is_promo = e['perf'] >= 3 and random.random() < (0.38 if e['perf'] == 4 else 0.22)
        change_t = 'Promotion' if is_promo else random.choices(
            ['Adjustment', 'Transfer'], weights=[0.70, 0.30])[0]

        inc       = random.uniform(0.04, 0.13) if is_promo else random.uniform(0.01, 0.05)
        new_sal   = int(cur_sal * (1 + inc))

        titles    = e['titles_list']
        if is_promo and cur_title in titles:
            idx       = titles.index(cur_title)
            new_title = titles[min(idx + 1, len(titles) - 1)]
        else:
            new_title = cur_title

        jh_rows.append({
            'EmployeeID':    e['emp_id'],
            'ChangeDate':    fmt(next_d),
            'FromJobTitle':  cur_title,
            'ToJobTitle':    new_title,
            'SalaryBefore':  cur_sal,
            'SalaryAfter':   new_sal,
            'ChangeType':    change_t,
            'DepartmentCode': e['dept'],
            'Site':          e['site'],
        })
        cur_date, cur_sal, cur_title = next_d, new_sal, new_title

pd.DataFrame(jh_rows).to_csv(
    os.path.join(OUTPUT_DIR, 'JobHistory.csv'),
    index=False, encoding='utf-8-sig',
)
print(f"[JobHistory]   {len(jh_rows):>5} lignes")


# ══════════════════════════════════════════════════════════════════════════════
# 4. FORMATIONS_LEARNING.CSV
# ══════════════════════════════════════════════════════════════════════════════

form_rows = []

for e in employees:
    if random.random() > 0.82:   # ~82 % des employés formés
        continue

    end        = e['term_date'] or date(YEAR_MAX, 6, 30)
    form_start = max(e['hire_date'], date(DATA_YEAR_MIN, 1, 1))
    dept_f     = DEPT_TO_FORMATION[e['dept']]

    n_form = random.choices([1, 2, 3, 4, 5], weights=[0.24, 0.30, 0.26, 0.13, 0.07])[0]
    if e['perf'] >= 3:
        n_form = min(n_form + 1, 7)

    seen = set()
    for _ in range(n_form):
        available = [t for t in TRAINING_CATALOG if t[0] not in seen]
        if not available or (end - form_start).days < 30:
            break

        t_name, t_days, t_cmin, t_cmax = random.choice(available)
        seen.add(t_name)

        days_range = (end - form_start).days
        start      = form_start + timedelta(days=random.randint(0, days_range - 10))
        duration   = max(1, t_days + random.randint(-1, 1))
        comp_d     = start + timedelta(days=duration)

        if comp_d <= date(YEAR_MAX, 6, 30):
            st_w = [0.72, 0.18, 0.10]
        else:
            st_w = [0.30, 0.55, 0.15]
        status = random.choices(['Completed', 'InProgress', 'Cancelled'], weights=st_w)[0]

        cost = round(random.uniform(t_cmin, t_cmax), 2)
        form_rows.append({
            'employee_id':           f'EMP_{e["emp_id"]}',
            'employee_department':   dept_f,
            'training_name':         t_name,
            'training_duration_days': duration,
            'training_cost_eur':     cost if status == 'Completed' else round(cost * 0.30, 2),
            'start_date':            fmt_iso(start),
            'completion_date':       fmt_iso(comp_d) if status == 'Completed' else '',
            'completion_status':     status,
            'site':                  e['site'],
        })

        form_start = comp_d + timedelta(days=60)
        if form_start >= end:
            break

pd.DataFrame(form_rows).to_csv(
    os.path.join(OUTPUT_DIR, 'Formations_Learning.csv'),
    index=False, encoding='utf-8-sig',
)
print(f"[Formations]   {len(form_rows):>5} lignes")


# ══════════════════════════════════════════════════════════════════════════════
# 5. TIMESHEET_OPERATIONS.XLSX  (données mensuelles par année)
# ══════════════════════════════════════════════════════════════════════════════

ts_rows = []

for e in employees:
    dept_ts  = DEPT_TO_TIMESHEET[e['dept']]
    end      = e['term_date'] or date(YEAR_MAX, 12, 31)

    for year in range(max(DATA_YEAR_MIN, e['hire_date'].year), min(YEAR_MAX + 1, end.year + 1)):
        for month in range(1, 13):
            m_start = date(year, month, 1)
            if m_start < e['hire_date'] or m_start > end:
                continue

            # Absences (0-9 j/mois) corrélées à la satisfaction
            if e['satisf'] == 1:
                abs_d = random.randint(2, 9)
            elif e['satisf'] == 2:
                abs_d = random.randint(1, 6)
            elif e['satisf'] == 4:
                abs_d = random.randint(0, 3)
            else:
                abs_d = random.randint(0, 5)

            # Heures sup (0-60 h/mois) corrélées à la performance et au département
            if e['perf'] == 4:
                ot = random.randint(5, 55)
            elif e['perf'] == 3:
                ot = random.randint(0, 35)
            elif e['perf'] == 2:
                ot = random.randint(0, 20)
            else:
                ot = random.randint(0, 12)
            if e['dept'] in ('IT', 'RD', 'MGMT'):
                ot = min(int(ot * 1.3), 60)

            ts_rows.append({
                'Emp_Code':      e['emp_id'],
                'Department':    dept_ts,
                'Year':          year,
                'Month':         month,
                'OvertimeHours': ot,
                'AbsenceDays':   abs_d,
                'Site':          e['site'],
            })

pd.DataFrame(ts_rows).to_excel(
    os.path.join(OUTPUT_DIR, 'Timesheet_Operations.xlsx'),
    index=False, engine='openpyxl',
)
print(f"[Timesheet]    {len(ts_rows):>5} lignes")


# ══════════════════════════════════════════════════════════════════════════════
# 6. EVALUATIONS_SEMESTRAL.DB  (SQLite)
# ══════════════════════════════════════════════════════════════════════════════

db_path = os.path.join(OUTPUT_DIR, 'Evaluations_Semestral.db')
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
conn.execute('''
    CREATE TABLE evaluations (
        id                    INTEGER PRIMARY KEY AUTOINCREMENT,
        emp_id                INTEGER NOT NULL,
        Department            TEXT,
        Semester              TEXT,
        EvaluationScore       INTEGER,
        ObjectivesAchieved_Pct INTEGER,
        PromotionRecommended  TEXT,
        Site                  TEXT
    )
''')

eval_rows = []
EVAL_YEAR_MAX = 2023

for e in employees:
    end = e['term_date'] or date(EVAL_YEAR_MAX, 12, 31)

    for year in range(DATA_YEAR_MIN, EVAL_YEAR_MAX + 1):
        for sem in (1, 2):
            sem_start = date(year, 1 if sem == 1 else 7, 1)
            sem_end   = date(year, 6 if sem == 1 else 12, 30)

            if sem_end < e['hire_date'] or sem_start > end:
                continue
            if random.random() > 0.87:   # ~87 % de couverture par semestre
                continue

            # Score évaluation (1-5) corrélé à la performance
            perf = e['perf']
            if perf == 4:
                score = random.choices([3, 4, 5], weights=[0.15, 0.48, 0.37])[0]
            elif perf == 3:
                score = random.choices([2, 3, 4, 5], weights=[0.10, 0.46, 0.34, 0.10])[0]
            elif perf == 2:
                score = random.choices([1, 2, 3, 4], weights=[0.14, 0.50, 0.31, 0.05])[0]
            else:
                score = random.choices([1, 2, 3], weights=[0.52, 0.38, 0.10])[0]

            # Objectifs atteints (%) corrélé au score + bruit
            obj = max(5, min(100, score * 18 + random.randint(-15, 15)))

            promo = 'Yes' if (score >= 4 and obj >= 75 and perf >= 3) else 'No'

            eval_rows.append((
                e['emp_id'], e['dept'],
                f'S{sem}-{year}', score, obj, promo, e['site'],
            ))

conn.executemany(
    'INSERT INTO evaluations '
    '(emp_id, Department, Semester, EvaluationScore, ObjectivesAchieved_Pct, PromotionRecommended, Site) '
    'VALUES (?, ?, ?, ?, ?, ?, ?)',
    eval_rows,
)
conn.commit()
conn.close()
print(f"[Evaluations]  {len(eval_rows):>5} lignes")

print('\n[OK] Les 5 sources ont ete generees dans', OUTPUT_DIR)
