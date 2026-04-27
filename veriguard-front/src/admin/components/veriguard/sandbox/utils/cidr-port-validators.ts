// M1 sandbox preset network-rule validator (spec §6.2): UI-only.
// Intentionally permissive — uses simple regex without a network library.
// Known accepted-but-malformed inputs that the back-end / iptables runtime
// will still reject:
//   - IPv6 with multiple "::" shorthands (e.g. "::1::/64")
//   - IPv4 octets with leading zeros (e.g. "010.0.0.0/8")

const IPV4_OCTET = '(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)';
const IPV4 = `${IPV4_OCTET}(?:\\.${IPV4_OCTET}){3}`;
const IPV4_CIDR = new RegExp(`^${IPV4}/(?:[0-9]|[12][0-9]|3[0-2])$`);
const IPV6_CIDR = /^([0-9A-Fa-f:]+)\/(?:[0-9]|[1-9][0-9]|1[01][0-9]|12[0-8])$/;

export const isValidCidr = (input: string): boolean => {
  if (!input) return false;
  if (IPV4_CIDR.test(input)) return true;
  if (IPV6_CIDR.test(input)) {
    const [addr] = input.split('/');
    const groups = addr.split(':');
    if (groups.length > 8) return false;
    return groups.every(g => g === '' || /^[0-9A-Fa-f]{1,4}$/.test(g));
  }
  return false;
};

export const isValidPortExpression = (input: string): boolean => {
  if (!input) return false;
  if (input === 'all' || input === 'none') return true;
  return input.split(',').every((part) => {
    const trimmed = part.trim();
    if (/^\d+$/.test(trimmed)) {
      const n = Number(trimmed);
      return n >= 1 && n <= 65535;
    }
    const range = /^(\d+)-(\d+)$/.exec(trimmed);
    if (range) {
      const a = Number(range[1]);
      const b = Number(range[2]);
      return a >= 1 && a <= 65535 && b >= a && b <= 65535;
    }
    return false;
  });
};
