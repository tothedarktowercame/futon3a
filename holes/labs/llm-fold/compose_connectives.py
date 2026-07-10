#!/usr/bin/env python3
"""compose_connectives.py — the BV connective grammar over the music cascades (E-llm-fold).

Same two cascades (blues, fugue), three CONNECTIVES — the only variable is HOW they compose:
  ◁  (seq, one-way signalling) = "begins as blues, ENDS as fugue"
        blues chorus -> hands off one-way to a fugue whose SUBJECT IS the blues head
        (signal flows blues->fugue: the fugue inherits the theme; the blues never reacts back).
  ⅋  (par, fully-signalling / coupled) = the BLEND = bluesfugue-A.mp3 (already built; reconciled).
  ⊗  (par, NON-signalling) = two independent layers in ONE frame (key A, tempo, bars), each
        composed from its OWN cascade with NO reference to the other. NOT simultaneous replay:
        purpose-built in a shared frame; delete either layer and the other still stands (the
        non-signalling test); you hear two parallel streams, occasionally rubbing (the honest cost).

Run:  python3 compose_connectives.py  -> connective-seq.mid (◁), connective-par.mid (⊗)
"""
import mido
from mido import Message, MidiFile, MidiTrack, MetaMessage
TPB=480
def bt(b): return int(round(b*TPB))
def tr(mel,s): return [((n+s) if n is not None else None,b) for n,b in mel]

# ---------- shared material, all in A ----------
# blues head (A blues scale), AAB
bluesA = [(57,1),(60,.667),(62,.333),(63,.667),(64,.333),(67,1), (67,1),(64,1),(62,2), (None,2),(60,1),(57,1), (None,4)]
bluesB = [(64,1),(67,.667),(69,.333),(67,1),(64,1), (62,1),(60,1),(57,2), (57,2),(None,1),(60,1), (62,1),(61,1),(60,1),(59,1)]
BLUES_MEL = bluesA+bluesA+bluesB                       # 12 bars
A7=[61,64,67]; D7=[54,57,60]; E7=[56,59,62]
BARS12=[A7,D7,A7,A7, D7,D7,A7,A7, E7,D7,A7,E7]
boog={tuple(A7):[45,52,54,55],tuple(D7):[50,57,59,60],tuple(E7):[52,59,61,62]}
def bbar(c): return [(n,1) for n in boog[tuple(c)]]
BLUES_BASS=[];
for c in BARS12: BLUES_BASS+=bbar(c)
BLUES_COMP=[(c,4) for c in BARS12]

# fugue subject in A (diatonic A major) for the INDEPENDENT fugue layer (⊗)
FUG_SUBJ=[(69,1),(71,1),(73,1),(76,1),(74,1),(73,1),(71,2)]     # A B C# E | D C# B  (high register)
FUG_ANS = tr(FUG_SUBJ,7)                                         # answer up a 5th
FUG_CS  =[(76,1),(74,1),(73,1),(71,1),(73,1),(71,1),(69,2)]      # a countersubject line

# blues head as a FUGUE SUBJECT (for ◁: the one-way blues->fugue signal)
BSUBJ=[(57,1),(60,.667),(62,.333),(64,1),(67,1), (64,1),(62,1),(57,2)]   # bluesy subject, 2 bars

def mono(voice,ch,vel,t0=0):
    ev=[];t=t0
    for n,b in voice:
        d=bt(b)
        if n is not None: ev+=[(t,Message('note_on',note=n,velocity=vel,channel=ch)),(t+d,Message('note_off',note=n,velocity=0,channel=ch))]
        t+=d
    return ev,t
def chords(voice,ch,vel,t0=0):
    ev=[];t=t0
    for ns,b in voice:
        d=bt(b)
        for n in ns: ev+=[(t,Message('note_on',note=n,velocity=vel,channel=ch)),(t+d,Message('note_off',note=n,velocity=0,channel=ch))]
        t+=d
    return ev
def drums(nbars,t0=0):
    ev=[]
    for bar in range(nbars):
        o=t0+bt(bar*4)
        for p in (0,.667,1,1.667,2,2.667,3,3.667): ev+=[(o+bt(p),Message('note_on',note=51,velocity=46,channel=9)),(o+bt(p)+20,Message('note_off',note=51,velocity=0,channel=9))]
        for p in (0,2): ev+=[(o+bt(p),Message('note_on',note=36,velocity=86,channel=9)),(o+bt(p)+60,Message('note_off',note=36,velocity=0,channel=9))]
        for p in (1,3): ev+=[(o+bt(p),Message('note_on',note=38,velocity=80,channel=9)),(o+bt(p)+60,Message('note_off',note=38,velocity=0,channel=9))]
    return ev
def mktrack(ev,program=None,ch=0):
    t=MidiTrack()
    if program is not None: t.append(Message('program_change',program=program,channel=ch,time=0))
    last=0
    for tm,m in sorted(ev,key=lambda e:e[0]): t.append(m.copy(time=tm-last)); last=tm
    return t
def tempo_track():
    tt=MidiTrack(); tt.append(MetaMessage('set_tempo',tempo=mido.bpm2tempo(96),time=0))
    tt.append(MetaMessage('time_signature',numerator=4,denominator=4,time=0)); return tt

# ---------- ◁  blues  THEN  fugue-on-the-blues-subject (one-way handoff) ----------
def build_seq():
    mid=MidiFile(ticks_per_beat=TPB); mid.tracks.append(tempo_track())
    # bars 1-12: the blues
    mel,_=mono(BLUES_MEL,0,92); cmp=chords(BLUES_COMP,3,54); bas,_=mono(BLUES_BASS,2,88); drm=drums(12)
    T=bt(12*4)  # fugue starts at bar 13
    # bars 13-20: fugue (organ) on the BLUES subject — subject, answer(+5), bass entry, cadence
    s,_=mono(BSUBJ,0,80,t0=T)                                   # S subject (bars13-14)
    a,_=mono(tr(BSUBJ,5),1,78,t0=T+bt(8))                       # A answer up a 4th (bars15-16)
    b,_=mono(tr(BSUBJ,-12),2,82,t0=T+bt(16))                    # Bass entry (bars17-18)
    # bars 19-20 authentic cadence in A: E7 -> A
    cad=chords([(E7,4),([57,61,64],4)],3,60,t0=T+bt(24))        # E7 | A
    fugue_mel=s+a+b
    mid.tracks.append(mktrack(mel+fugue_mel,program=66,ch=0))   # sax (blues head) -> doubles as subject voice
    mid.tracks.append(mktrack(a,program=19,ch=1))               # organ answer voice
    mid.tracks.append(mktrack(bas+b,program=33,ch=2))           # bass: boogie then fugal entry
    mid.tracks.append(mktrack(cmp+cad,program=4,ch=3))          # e-piano comp then cadence chords
    mid.tracks.append(mktrack(drm,ch=9))                        # drums only during the blues (stop at the fugue)
    mid.save("connective-seq.mid"); print("wrote connective-seq.mid  (◁ blues -> fugue-on-blues-subject)")

# ---------- ⊗  two independent layers in ONE frame (non-signalling) ----------
def build_par():
    mid=MidiFile(ticks_per_beat=TPB); mid.tracks.append(tempo_track())
    # LAYER 1 (blues), composed from the blues cascade ALONE:
    mel,_=mono(BLUES_MEL,0,90); bas,_=mono(BLUES_BASS,2,86); cmp=chords(BLUES_COMP,3,50); drm=drums(12)
    # LAYER 2 (fugue), composed from the fugue cascade ALONE — NO reference to the blues changes:
    s,_=mono(FUG_SUBJ,1,70,t0=0)                                # subject (bars1-2)
    a,_=mono(FUG_ANS,4,68,t0=bt(8))                             # answer (bars3-4)
    cs,_=mono(FUG_CS,1,66,t0=bt(8))                             # countersubject vs the answer (bars3-4)
    b,_=mono(tr(FUG_SUBJ,-12),4,70,t0=bt(16))                  # bass-register entry (bars5-6)
    # a little continuing fugal counterpoint bars 7-12 (independent of the blues):
    cont1,_=mono([(76,2),(74,2),(73,2),(71,2),(69,2),(71,2),(73,2),(74,2),(76,4),(74,4)],1,60,t0=bt(24))
    cont2,_=mono([(69,4),(67,4),(66,4),(64,4),(62,4),(64,4)],4,58,t0=bt(24))
    mid.tracks.append(mktrack(mel,program=66,ch=0))            # blues sax
    mid.tracks.append(mktrack(bas,program=33,ch=2))            # blues bass
    mid.tracks.append(mktrack(cmp,program=4,ch=3))             # blues comp
    mid.tracks.append(mktrack(drm,ch=9))                       # blues drums
    mid.tracks.append(mktrack(s+cs+cont1,program=19,ch=1))    # fugue upper (organ) — INDEPENDENT
    mid.tracks.append(mktrack(a+b+cont2,program=20,ch=4))     # fugue lower (reed organ) — INDEPENDENT
    mid.save("connective-par.mid"); print("wrote connective-par.mid  (⊗ blues ∥ fugue, non-signalling)")

if __name__=="__main__":
    build_seq(); build_par()
