import type { ReactNode } from 'react';
import { motion } from 'motion/react';

interface FadeRiseProps {
  children: ReactNode;
  className?: string;
  delay?: number;
}

export function FadeRise({ children, className, delay = 0 }: FadeRiseProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.12 }}
      transition={{ duration: 0.45, delay, ease: [0.16, 1, 0.3, 1] }}
      className={className}
    >
      {children}
    </motion.div>
  );
}
