# Genera pagar-reserva.svg (diagrama de secuencia de "Pagar reserva") sin dependencias.
from xml.sax.saxutils import escape

P = ["Turista", "PagoWebController", "PagoService", "Base de datos", "PasarelaClient", "Pasarela\n(/pasarela/pagar)"]
X = [90, 280, 500, 730, 940, 1160]
W, TOP, STEP = 1300, 90, 34
steps = [
 ("msg",0,1,"POST /panel/turista/reservas/{id}/pagar (tarjeta, venc., CVV)",0),
 ("msg",1,2,"validarReservaPagable() y validarTarjeta()",0),
 ("msg",2,1,"mensaje de error o null",1),
 ("frame","alt  datos inválidos / reserva cancelada, vencida o ya pagada"),
 ("msg",1,0,"mensaje de error (NO se llama a la pasarela)",1),
 ("end",),
 ("msg",1,2,"pagar(turistaId, reservaId, tarjeta, venc., cvv)",0),
 ("msg",2,3,"SELECT reserva FOR UPDATE (bloqueo anti doble cobro)",0),
 ("msg",3,2,"reserva (PENDIENTE_PAGO)",1),
 ("self",2,"monto = precio x cupos (RNE #11)"),
 ("msg",2,4,"cobrar(tarjeta, venc., cvv, monto)",0),
 ("msg",4,5,"POST /pasarela/pagar  (RestClient, timeout 3 s)",0),
 ("frame","alt  tarjeta ...1111  ->  APROBADO"),
 ("msg",5,4,"200 {APROBADO, código}",1),
 ("msg",4,2,"RespuestaPasarela",1),
 ("msg",2,3,"INSERT Pago (APROBADO, últimos 4, código)",0),
 ("msg",2,3,"UPDATE reserva: estado = CONFIRMADA",0),
 ("msg",2,1,"Pago APROBADO",1),
 ("msg",1,0,"\"Pago aprobado, tu reserva quedó confirmada\"",1),
 ("else","else  tarjeta ...2222  ->  RECHAZADO"),
 ("msg",5,4,"200 {RECHAZADO, fondos insuficientes}",1),
 ("msg",4,2,"RespuestaPasarela",1),
 ("msg",2,3,"INSERT Pago (RECHAZADO)",0),
 ("self",2,"la reserva sigue PENDIENTE_PAGO"),
 ("msg",2,1,"Pago RECHAZADO",1),
 ("msg",1,0,"\"Pago rechazado... podés reintentar hasta las HH:mm\"",1),
 ("else","else  ...3333 (timeout)  o  pasarela caída (5xx)"),
 ("msg",4,2,"ResourceAccessException (timeout / 503)",2),
 ("msg",2,3,"INSERT Pago (ERROR, sin código)",0),
 ("self",2,"la reserva sigue PENDIENTE_PAGO; no se rompe"),
 ("msg",2,1,"Pago ERROR",1),
 ("msg",1,0,"\"El servicio de pagos no respondió... reintentá\"",1),
 ("end",),
]
# calcular alturas
y = TOP + 70; ops = []; frames = []; stack = []
for s in steps:
    k = s[0]
    if k == "frame":
        y += 8; stack.append([s[1], y, []]); y += 24
    elif k == "else":
        stack[-1][2].append((y - 6, s[1])); y += 22
    elif k == "end":
        lab, y0, seps = stack.pop(); frames.append((lab, y0, y + 4, seps)); y += 14
    elif k == "msg":
        ops.append(("msg", y + 14, *s[1:])); y += STEP
    else:
        ops.append(("self", y + 14, *s[1:])); y += STEP
H = y + 30
o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="Arial, sans-serif" font-size="12.5">',
     '<defs><marker id="a" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto"><path d="M0,0 L10,4 L0,8 z" fill="#1b2a41"/></marker>'
     '<marker id="x" markerWidth="10" markerHeight="10" refX="5" refY="5" orient="auto"><path d="M1,1 L9,9 M9,1 L1,9" stroke="#b9483a" stroke-width="2"/></marker></defs>',
     f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
     f'<text x="{W/2}" y="28" text-anchor="middle" font-size="18" font-weight="bold" fill="#1b2a41">Diagrama de secuencia: Pagar reserva (CU-T07)</text>']
# marcos
cols = {"alt": "#fdf3ec"}
for lab, y0, y1, seps in frames:
    o.append(f'<rect x="40" y="{y0}" width="{W-80}" height="{y1-y0}" fill="#fdf3ec" fill-opacity="0.55" stroke="#e0713a" stroke-width="1.3"/>')
    o.append(f'<text x="48" y="{y0+16}" font-weight="bold" fill="#c95f2b">{escape(lab)}</text>')
    for ys, t in seps:
        o.append(f'<line x1="40" y1="{ys}" x2="{W-40}" y2="{ys}" stroke="#e0713a" stroke-dasharray="6 4"/>')
        o.append(f'<text x="48" y="{ys+16}" font-weight="bold" fill="#c95f2b">{escape(t)}</text>')
# líneas de vida
for i, (n, x) in enumerate(zip(P, X)):
    o.append(f'<line x1="{x}" y1="{TOP+40}" x2="{x}" y2="{H-14}" stroke="#9aa3ad" stroke-dasharray="5 4"/>')
    lines = n.split("\n")
    o.append(f'<rect x="{x-80}" y="{TOP-30}" width="160" height="{26+14*len(lines)}" rx="6" fill="#1b2a41"/>')
    for j, l in enumerate(lines):
        o.append(f'<text x="{x}" y="{TOP-10+14*j}" text-anchor="middle" fill="#fff" font-weight="bold">{escape(l)}</text>')
# mensajes
for op in ops:
    if op[0] == "msg":
        _, yy, a, b, t, d = op
        xa, xb = X[a], X[b]
        dash = ' stroke-dasharray="6 4"' if d else ''
        err = d == 2
        col = "#b9483a" if err else "#1b2a41"
        mk = "x" if err else "a"
        o.append(f'<line x1="{xa}" y1="{yy}" x2="{xb + (-2 if xb>xa else 2)}" y2="{yy}" stroke="{col}" stroke-width="1.4"{dash} marker-end="url(#{mk})"/>')
        o.append(f'<text x="{(xa+xb)/2}" y="{yy-5}" text-anchor="middle" fill="{col}">{escape(t)}</text>')
    else:
        _, yy, p, t = op
        x = X[p]
        o.append(f'<rect x="{x+6}" y="{yy-12}" width="{7*len(t)*0.95:.0f}" height="22" fill="#fff6bf" stroke="#d9c75a"/>')
        o.append(f'<text x="{x+12}" y="{yy+3}">{escape(t)}</text>')
o.append('</svg>')
open("pagar-reserva.svg", "w", encoding="utf-8").write("\n".join(o))
print(W, H)
